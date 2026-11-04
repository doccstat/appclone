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
        static Item heading(android.content.Context context, Inventory.Entry entry) {
            return new Item(HEADING, "app:" + entry.app.pkg, entry.app.label,
                    context.getResources().getQuantityString(R.plurals.clone_count, entry.profiles.size(), entry.profiles.size()), entry, null);
        }
        static Item instance(android.content.Context context, Inventory.Entry entry, Inventory.Profile profile, boolean appHeading) {
            String state = Inventory.state(context, profile, entry.app.pkg);
            String detail = appHeading ? context.getString(R.string.app_summary, state, Ui.bidi(context, entry.app.pkg))
                    : context.getString(R.string.instance_status, Integer.toString(profile.clone.id), state);
            return new Item(appHeading ? APP_INSTANCE : INSTANCE,
                    "instance:" + profile.clone.id + ":" + profile.serial + ":" + entry.app.pkg,
                    appHeading ? entry.app.label : profile.clone.name, detail, entry, profile);
        }
        static Item profile(android.content.Context context, Inventory.Profile profile, int count) {
            return new Item(PROFILE, "profile:" + profile.clone.id + ":" + profile.serial, profile.clone.name,
                    context.getString(R.string.profile_status, Integer.toString(profile.clone.id),
                            context.getString(profile.clone.running ? R.string.running : R.string.stopped),
                            context.getResources().getQuantityString(R.plurals.app_count, count, count)), null, profile);
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
        header.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        LinearLayout notice = Ui.row(activity);
        status = Ui.type(activity, "", com.google.android.material.R.style.TextAppearance_Material3_BodySmall, R.color.muted);
        status.setMaxLines(2);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setMinHeight(Ui.dp(activity, 48));
        status.setGravity(android.view.Gravity.CENTER_VERTICAL);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        notice.addView(status, Ui.weight(1));
        retry = Ui.quiet(activity, activity.getString(R.string.retry));
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
        final TextView title, subtitle, count;
        final ImageView icon;
        final ImageButton open, more;
        final LinearLayout labels;

        Row(InventoryActivity activity, int kind) {
            super(Ui.column(activity));
            this.activity = activity;
            LinearLayout root = (LinearLayout) itemView;
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(Ui.dp(activity, 16), Ui.dp(activity, kind == HEADING || kind == EMPTY ? 16 : 0), Ui.dp(activity, 16), 0);
            root.setLayoutParams(params);
            LinearLayout line = Ui.row(activity);
            line.setPaddingRelative(Ui.dp(activity, kind == INSTANCE ? 12 : 0), Ui.dp(activity, 8), 0, Ui.dp(activity, 8));
            icon = new ImageView(activity);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            line.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
            icon.setVisibility(kind == HEADING || kind == APP_INSTANCE ? View.VISIBLE : View.GONE);
            labels = Ui.column(activity);
            title = Ui.type(activity, "", kind == HEADING || kind == EMPTY
                    ? com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
                    : com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, R.color.ink);
            title.setAccessibilityHeading(kind == HEADING || kind == EMPTY);
            subtitle = Ui.type(activity, "", com.google.android.material.R.style.TextAppearance_Material3_BodyMedium, R.color.muted);
            subtitle.setId(R.id.row_metadata);
            if (kind == HEADING) {
                subtitle.setMaxLines(1);
                subtitle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            }
            labels.addView(title); labels.addView(subtitle);
            line.addView(labels, Ui.weightWithStart(activity, 1, kind == HEADING || kind == APP_INSTANCE ? 12 : 0));
            count = Ui.type(activity, "", com.google.android.material.R.style.TextAppearance_Material3_LabelMedium, R.color.muted);
            count.setMaxWidth(Ui.dp(activity, 80));
            count.setPaddingRelative(Ui.dp(activity, 8), 0, 0, 0);
            line.addView(count);
            count.setVisibility(kind == HEADING ? View.VISIBLE : View.GONE);
            open = Ui.iconButton(activity, R.drawable.ic_open);
            open.setId(R.id.open_clone);
            open.setTooltipText(activity.getString(R.string.open));
            line.addView(open);
            open.setVisibility(kind == INSTANCE || kind == APP_INSTANCE ? View.VISIBLE : View.GONE);
            more = Ui.overflow(activity);
            more.setId(R.id.row_actions);
            line.addView(more);
            more.setVisibility(kind == EMPTY ? View.GONE : View.VISIBLE);
            root.addView(line);
            if (kind != HEADING && kind != EMPTY) root.addView(Ui.divider(activity, kind == INSTANCE ? 12 : 0));
        }

        void bind(Item item) {
            title.setText(item.title);
            subtitle.setText(item.detail);
            icon.setImageDrawable(item.entry == null ? null : item.entry.icon);
            if (item.kind == HEADING) {
                subtitle.setText(item.entry.app.pkg);
                subtitle.setTooltipText(item.entry.app.pkg);
                count.setText(item.detail);
                more.setContentDescription(activity.getString(R.string.actions_for, Ui.bidi(activity, item.title)));
                more.setTooltipText(activity.getString(R.string.app_actions));
                more.setOnClickListener(v -> {
                    PopupMenu menu = new PopupMenu(activity, more);
                    menu.getMenu().add(R.string.add_another_clone).setOnMenuItemClickListener(action -> {
                        AddClone.chooseProfile(activity, item.entry.app); return true;
                    });
                    menu.getMenu().add(R.string.app_details).setOnMenuItemClickListener(action -> {
                        activity.startActivity(new Intent(activity, AppDetailActivity.class)
                                .putExtra(AppDetailActivity.EXTRA_PKG, item.entry.app.pkg)); return true;
                    });
                    menu.show();
                });
            } else if (item.kind == INSTANCE || item.kind == APP_INSTANCE) {
                AppControls.bind(activity, item.entry, item.profile, open, more);
            } else if (item.kind == PROFILE) {
                subtitle.setText(item.detail);
                labels.setBackground(Ui.selectable(activity));
                labels.setMinimumHeight(Ui.dp(activity, 48));
                labels.setContentDescription(activity.getString(R.string.view_apps_in_profile, Ui.bidi(activity, item.title), item.detail));
                labels.setOnClickListener(v -> ProfileControls.open(activity, item.profile));
                more.setContentDescription(activity.getString(R.string.actions_for, Inventory.location(activity, item.profile)));
                more.setTooltipText(activity.getString(R.string.profile_actions));
                more.setOnClickListener(v -> ProfileControls.menu(activity, item.profile, more));
            }
        }
    }
}
