#!/usr/bin/env python3
"""Build with installed SDK tools; never connects to a device or creates a key."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
API_SHA256 = 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'


def certificate_digest(value):
    digest = value.replace(':', '').strip().lower()
    if not re.fullmatch(r'[0-9a-f]{64}', digest):
        raise ValueError('SIGNING_CERT_SHA256 must be a 64-hex-digit certificate SHA-256 fingerprint')
    return digest


def main():
    self_check = '--self-check' in sys.argv[1:]
    release = '--release' in sys.argv[1:]
    if release:
        required = ('SIGNING_KEYSTORE', 'SIGNING_STORE_PASSWORD',
                    'SIGNING_KEY_PASSWORD', 'SIGNING_KEY_ALIAS')
        missing = [name for name in required if not os.environ.get(name)]
        if missing:
            raise SystemExit('Release signing requires: ' + ', '.join(missing))
        if not Path(os.environ['SIGNING_KEYSTORE']).is_file():
            raise SystemExit('Release keystore does not exist; refusing to generate a replacement')
        try:
            expected_signer = certificate_digest(os.environ.get('SIGNING_CERT_SHA256', ''))
        except ValueError as error:
            raise SystemExit(str(error))
    else:
        expected_signer = None
    windows = os.name == 'nt'
    sdk = Path(os.environ['ANDROID_HOME'])
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    android = sdk / 'platforms/android-36/android.jar'
    tools_root = sdk / 'build-tools'
    tools = Path(os.environ['BUILD_TOOLS']) if os.environ.get('BUILD_TOOLS') else tools_root / '37.0.0'
    if not tools.is_dir():
        marker = 'aapt2.exe' if windows else 'aapt2'
        candidates = sorted((path for path in tools_root.glob('*') if (path / marker).is_file()),
                            key=lambda path: path.name, reverse=True)
        if not candidates:
            raise SystemExit('No Android build-tools installation found')
        tools = candidates[0]
    api = Path(os.environ['XPOSED_API_JAR'])
    if hashlib.sha256(api.read_bytes()).hexdigest() != API_SHA256:
        raise SystemExit('Unexpected Xposed API 82 jar; see motoappclone/README.md for URL and hash')
    out = Path(os.environ.get('BUILD_DIR', Path.home() / '.cache/appclone')).resolve()
    if out == ROOT or ROOT in out.parents:
        raise SystemExit('BUILD_DIR must be outside the source tree')
    out.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, PATH=str(java) + os.pathsep + os.environ.get('PATH', ''),
               PYTHONDONTWRITEBYTECODE='1')

    def run(*args):
        subprocess.run([str(arg) for arg in args], check=True, env=env)

    def jtool(name):
        return java / (name + ('.exe' if windows else ''))

    def tool(name):
        suffix = ('.bat' if name in ('d8', 'apksigner') else '.exe') if windows else ''
        return tools / (name + suffix)

    with tempfile.TemporaryDirectory(prefix='build-', dir=out) as temporary:
        build = Path(temporary)
        for folder in ('classes', 'dex'):
            (build / folder).mkdir()
        (build / 'rjava').mkdir()
        run(tool('aapt2'), 'compile', '--dir', ROOT / 'res', '-o', build / 'resources.zip')
        run(tool('aapt2'), 'link', '-o', build / 'unsigned.apk', '-I', android,
            '--manifest', ROOT / 'AndroidManifest.xml', '--java', build / 'rjava',
            build / 'resources.zip')
        sources = sorted((ROOT / 'src').rglob('*.java'))
        if self_check:
            sources += sorted((ROOT / 'tests').rglob('*.java'))
        sources += sorted((build / 'rjava').rglob('*.java'))
        run(jtool('javac'), '--release', '8', '-cp', os.pathsep.join(map(str, (android, api))),
            '-d', build / 'classes', *sources)
        if self_check:
            run(jtool('java'), '-ea', '-cp', build / 'classes',
                'com.lixingchi.appclone.LabelsTest')
            return
        classes_jar = build / 'classes.jar'
        with zipfile.ZipFile(classes_jar, 'w') as jar:
            for cls in sorted((build / 'classes').rglob('*.class')):
                jar.write(cls, cls.relative_to(build / 'classes'))
        run(tool('d8'), '--lib', android, '--classpath', api, '--min-api', '36',
            '--output', build / 'dex', classes_jar)
        with zipfile.ZipFile(build / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
            apk.write(build / 'dex/classes.dex', 'classes.dex')
            apk.write(ROOT / 'assets/xposed_init', 'assets/xposed_init')
        unsigned = out / 'AppClone-unsigned.apk'
        run(tool('zipalign'), '-f', '4', build / 'unsigned.apk', unsigned)
        run(tool('zipalign'), '-c', '4', unsigned)
        output = unsigned
        # Signing is optional and uses an existing key. Never generate credentials in build caches.
        if 'SIGNING_KEYSTORE' in os.environ:
            key = Path(os.environ['SIGNING_KEYSTORE']).resolve()
            if not key.is_file():
                raise SystemExit('SIGNING_KEYSTORE must name an existing key')
            if ROOT in key.parents or '.cache' in key.parts:
                raise SystemExit('Keep signing credentials outside source and cache directories')
            output = out / 'AppClone.apk'
            run(tool('apksigner'), 'sign', '--ks', key,
                '--ks-pass', 'env:SIGNING_STORE_PASSWORD', '--key-pass', 'env:SIGNING_KEY_PASSWORD',
                '--ks-key-alias', os.environ['SIGNING_KEY_ALIAS'], '--out', output, unsigned)
            verify = subprocess.run([str(tool('apksigner')), 'verify', '--verbose',
                    '--print-certs', str(output)], check=True, env=env,
                    capture_output=True, text=True)
            if release:
                match = re.search(r'certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)',
                                  verify.stdout + verify.stderr)
                if not match or match.group(1).replace(':', '').lower() != expected_signer:
                    raise SystemExit('APK certificate does not match SIGNING_CERT_SHA256')
            run(tool('zipalign'), '-c', '4', output)
        checksum = hashlib.sha256(output.read_bytes()).hexdigest()
        (out / (output.name + '.sha256')).write_text(f'{checksum}  {output.name}\n')
        print(checksum, output)


if __name__ == '__main__':
    main()
