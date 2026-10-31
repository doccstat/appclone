package com.lixingchi.appclone;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

final class ScreenTestSupport {
    /** Bind off-screen rows too, without assuming RecyclerView eagerly creates every view. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static View row(InventoryList list, int position) {
        RecyclerView.Adapter adapter = list.getAdapter();
        RecyclerView.ViewHolder holder = adapter.createViewHolder(list, adapter.getItemViewType(position));
        adapter.bindViewHolder(holder, position);
        return holder.itemView;
    }

    static String text(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof InventoryList) {
            InventoryList list = (InventoryList) view;
            result.append(text(list.header));
            for (int i = 1; i < list.getAdapter().getItemCount(); i++) result.append(text(row(list, i)));
            return result.toString();
        }
        if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result.append(text(((ViewGroup) view).getChildAt(i)));
        return result.toString();
    }

    static TextView findText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findText(((ViewGroup) view).getChildAt(i), value);
            if (found != null) return found;
        }
        return null;
    }

    static <T> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T found = find(((ViewGroup) view).getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private ScreenTestSupport() { }
}
