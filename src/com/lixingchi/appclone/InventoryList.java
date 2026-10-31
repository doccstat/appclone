package com.lixingchi.appclone;

import android.content.Intent;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** One vertical scroller: app headings and every instance are independently recycled rows. */
final class InventoryList extends RecyclerView {
    static final int HEADING = 1, INSTANCE = 2, APP_INSTANCE = 3, PROFILE = 4, EMPTY = 5;
    final LinearLayout header;
    final TextView status;
    final Button retry;
    private final Rows adapter = new Rows();
    private List<Item> items = new ArrayList<>();
    private Parcelable pendingState;

    static final class Item {
        final int kind;
        final String key, title, detail;
        final Inventory.Entry entry;
        final Inventory.Profile profile;
        private Item(int kind, String key, String title, String detail, Inventory.Entry entry, Inventory.Profile profile) {
            this.kind = kind; this.key = key; this.title = title; this.detail = detail;
            this.entry = entry; this.profile = profile;
        }
        static Item heading(Inventory.Entry entry) {
            return new Item(HEADING, "app:" + entry.app.pkg, entry.app.label,
                    entry.profiles.size() + (entry.profiles.size() == 1 ? " clone" : " clones"), entry, null);
        }
        static Item instance(Inventory.Entry entry, Inventory.Profile profile, boolean appHeading) {
            return new Item(appHeading ? APP_INSTANCE : INSTANCE,
                    "instance:" + profile.clone.id + ":" + profile.serial + ":" + entry.app.pkg,
                    appHeading ? entry.app.label : profile.clone.name, Inventory.state(profile, entry.app.pkg), entry, profile);
        }
        static Item profile(Inventory.Profile profile, int count) {
            return new Item(PROFILE, "profile:" + profile.clone.id + ":" + profile.serial, profile.clone.name,
                    "User " + profile.clone.id + " · " + (profile.clone.running ? "Running" : "Stopped")
                            + " · " + count + " apps", null, profile);
        }
        static Item empty(String title, String detail) {
            return new Item(EMPTY, "empty", title, detail, null, null);
        }
    }

    InventoryList(InventoryActivity activity) {
        super(activity);
        setLayoutManager(new LinearLayoutManager(activity));
        setItemAnimator(null);
        setClipToPadding(false);
        setPadding(0, 0, 0, Ui.dp(activity, 16));
        header = Ui.column(activity);
        header.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), Ui.dp(activity, 8));
        LinearLayout notice = Ui.row(activity);
        status = Ui.type(activity, "", com.google.android.material.R.style.TextAppearance_Material3_BodySmall, R.color.muted);
        status.setMaxLines(2);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setMinHeight(Ui.dp(activity, 48));
        status.setGravity(android.view.Gravity.CENTER_VERTICAL);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        notice.addView(status, Ui.weight(1));
        retry = Ui.quiet(activity, "Retry");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> activity.refresh());
        notice.addView(retry);
        header.addView(notice);
        setAdapter(adapter);
    }

    void show(List<Item> next) {
        List<Item> before = items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return next.size(); }
            public boolean areItemsTheSame(int old, int now) {
                return before.get(old).kind == next.get(now).kind && before.get(old).key.equals(next.get(now).key);
            }
            public boolean areContentsTheSame(int old, int now) {
                Item a = before.get(old), b = next.get(now);
                return a.entry == b.entry && a.profile == b.profile && a.title.equals(b.title) && a.detail.equals(b.detail);
            }
        });
        items = new ArrayList<>(next);
        // Position zero is the persistent search/status header, not an inventory item.
        diff.dispatchUpdatesTo(new ListUpdateCallback() {
            public void onInserted(int position, int count) { adapter.notifyItemRangeInserted(position + 1, count); }
            public void onRemoved(int position, int count) { adapter.notifyItemRangeRemoved(position + 1, count); }
            public void onMoved(int from, int to) { adapter.notifyItemMoved(from + 1, to + 1); }
            public void onChanged(int position, int count, Object payload) { adapter.notifyItemRangeChanged(position + 1, count, payload); }
        });
        if (pendingState != null && !items.isEmpty()) {
            getLayoutManager().onRestoreInstanceState(pendingState);
            pendingState = null;
        }
    }

    Parcelable position() { return pendingState != null ? pendingState : getLayoutManager().onSaveInstanceState(); }
    void restorePosition(Parcelable state) { pendingState = state; }
    void top() { pendingState = null; scrollToPosition(0); }

    private final class Rows extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return items.size() + 1; }
        @Override public int getItemViewType(int position) { return position == 0 ? 0 : items.get(position - 1).kind; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            if (type != 0) return new Row((InventoryActivity) getContext(), type);
            FrameLayout frame = new FrameLayout(getContext());
            frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(frame) { };
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position != 0) { ((Row) holder).bind(items.get(position - 1)); return; }
            if (header.getParent() != holder.itemView) {
                if (header.getParent() != null) ((ViewGroup) header.getParent()).removeView(header);
                ((FrameLayout) holder.itemView).addView(header);
            }
        }
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final InventoryActivity activity;
        final TextView title, subtitle, detail;
        final ImageView icon;
        final TextView avatar;
        final Button open;
        final ImageButton more;
        final LinearLayout labels;

        Row(InventoryActivity activity, int kind) {
            super(Ui.column(activity));
            this.activity = activity;
            LinearLayout root = (LinearLayout) itemView;
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            LinearLayout line = Ui.row(activity);
            line.setPadding(Ui.dp(activity, kind == INSTANCE ? 32 : 16), Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12));
            if (kind == HEADING) {
                root.setPadding(0, Ui.dp(activity, 16), 0, 0);
                line.setBackgroundColor(Ui.color(activity, R.color.surface));
            }
            icon = new ImageView(activity);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            line.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
            icon.setVisibility(kind == HEADING || kind == APP_INSTANCE ? View.VISIBLE : View.GONE);
            avatar = Ui.avatar(activity, "");
            avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            line.addView(avatar);
            avatar.setVisibility(kind == PROFILE ? View.VISIBLE : View.GONE);
            labels = Ui.column(activity);
            title = Ui.type(activity, "", kind == HEADING || kind == EMPTY
                    ? com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
                    : com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, R.color.ink);
            title.setAccessibilityHeading(kind == HEADING || kind == EMPTY);
            subtitle = Ui.type(activity, "", com.google.android.material.R.style.TextAppearance_Material3_BodyMedium, R.color.muted);
            detail = Ui.type(activity, "", com.google.android.material.R.style.TextAppearance_Material3_BodySmall, R.color.muted);
            labels.addView(title); labels.addView(subtitle); labels.addView(detail);
            line.addView(labels, Ui.weightWithStart(activity, 1, kind == INSTANCE || kind == EMPTY ? 0 : 16));
            open = Ui.quiet(activity, "Open");
            open.setMinWidth(0);
            open.setMinimumWidth(0);
            open.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
            line.addView(open);
            open.setVisibility(kind == INSTANCE || kind == APP_INSTANCE ? View.VISIBLE : View.GONE);
            more = Ui.overflow(activity);
            line.addView(more);
            more.setVisibility(kind == EMPTY ? View.GONE : View.VISIBLE);
            root.addView(line);
            if (kind != HEADING && kind != EMPTY) root.addView(Ui.divider(activity, kind == INSTANCE ? 32 : 16));
        }

        void bind(Item item) {
            title.setText(item.title);
            detail.setText(item.detail);
            detail.setVisibility(View.VISIBLE);
            icon.setImageDrawable(item.entry == null ? null : item.entry.icon);
            if (item.kind == HEADING) {
                subtitle.setText(item.entry.app.pkg);
                more.setContentDescription("Actions for " + item.title);
                more.setTooltipText("App actions");
                more.setOnClickListener(v -> {
                    PopupMenu menu = new PopupMenu(activity, more);
                    menu.getMenu().add("Add another clone").setOnMenuItemClickListener(action -> {
                        AddClone.chooseProfile(activity, item.entry.app); return true;
                    });
                    menu.getMenu().add("App details").setOnMenuItemClickListener(action -> {
                        activity.startActivity(new Intent(activity, AppDetailActivity.class)
                                .putExtra(AppDetailActivity.EXTRA_PKG, item.entry.app.pkg)); return true;
                    });
                    menu.show();
                });
            } else if (item.kind == INSTANCE || item.kind == APP_INSTANCE) {
                subtitle.setText(item.kind == INSTANCE ? "User " + item.profile.clone.id : item.entry.app.pkg);
                AppControls.bind(activity, item.entry, item.profile, open, more);
            } else if (item.kind == PROFILE) {
                avatar.setText(Integer.toString(item.profile.clone.id));
                subtitle.setText(item.detail);
                detail.setVisibility(View.GONE);
                labels.setBackground(Ui.selectable(activity));
                labels.setMinimumHeight(Ui.dp(activity, 48));
                labels.setContentDescription("View apps in " + item.title + " · " + item.detail);
                labels.setOnClickListener(v -> ProfileControls.open(activity, item.profile));
                more.setContentDescription("Actions for " + Inventory.location(item.profile));
                more.setTooltipText("Profile actions");
                more.setOnClickListener(v -> ProfileControls.menu(activity, item.profile, more));
            } else {
                subtitle.setText(item.detail);
                detail.setVisibility(View.GONE);
            }
        }
    }
}
