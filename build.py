#!/usr/bin/env python3
"""Build Material resources with Gradle; sign only with an explicitly supplied key."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

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
    lint = '--lint' in sys.argv[1:]
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
    api = Path(os.environ['XPOSED_API_JAR'])
    if hashlib.sha256(api.read_bytes()).hexdigest() != API_SHA256:
        raise SystemExit(f'Unexpected Xposed API 82 jar; expected sha256 {API_SHA256}')
    out = Path(os.environ.get('BUILD_DIR', Path.home() / '.cache/appclone')).resolve()
    if out != ROOT / 'dist' and (out == ROOT or ROOT in out.parents):
        raise SystemExit('BUILD_DIR must be outside the source tree (or exactly ./dist)')
    out.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    env.setdefault('GRADLE_USER_HOME', str(Path.home() / '.cache/appclone/gradle-home'))
    windows = os.name == 'nt'
    command = [str(ROOT / ('gradlew.bat' if windows else 'gradlew')),
               '--no-daemon', '--no-problems-report', '--project-cache-dir', str(out / 'project-cache'),
               f'-PappcloneBuildDir={out / "gradle"}']
    if env.get('ANDROID_AAPT2'):
        command.append('-Pandroid.aapt2FromMavenOverride=' + env['ANDROID_AAPT2'])
    command.append('testReleaseUnitTest' if self_check else 'lintRelease' if lint else 'assembleRelease')
    subprocess.run(command, check=True, cwd=ROOT, env=env)
    if self_check or lint:
        return
    unsigned = out / 'AppClone-unsigned.apk'
    shutil.copyfile(out / 'gradle/outputs/apk/release/AppClone-release-unsigned.apk', unsigned)
    output = unsigned
    if 'SIGNING_KEYSTORE' in env:
        key = Path(env['SIGNING_KEYSTORE']).resolve()
        if not key.is_file():
            raise SystemExit('SIGNING_KEYSTORE must name an existing key')
        if ROOT in key.parents or '.cache' in key.parts:
            raise SystemExit('Keep signing credentials outside source and cache directories')
        tools = Path(env.get('BUILD_TOOLS', Path(env['ANDROID_HOME']) / 'build-tools/36.0.0'))
        signer = tools / ('apksigner.bat' if windows else 'apksigner')
        output = out / 'AppClone.apk'
        subprocess.run([str(signer), 'sign', '--ks', str(key),
                        '--ks-pass', 'env:SIGNING_STORE_PASSWORD',
                        '--key-pass', 'env:SIGNING_KEY_PASSWORD',
                        '--ks-key-alias', env['SIGNING_KEY_ALIAS'],
                        '--out', str(output), str(unsigned)], check=True, env=env)
        verify = subprocess.run([str(signer), 'verify', '--verbose', '--print-certs', str(output)],
                                check=True, env=env, capture_output=True, text=True)
        if release:
            match = re.search(r'certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)',
                              verify.stdout + verify.stderr)
            if not match or match.group(1).replace(':', '').lower() != expected_signer:
                raise SystemExit('APK certificate does not match SIGNING_CERT_SHA256')
    checksum = hashlib.sha256(output.read_bytes()).hexdigest()
    (out / (output.name + '.sha256')).write_text(f'{checksum}  {output.name}\n')
    print(checksum, output)


if __name__ == '__main__':
    main()
