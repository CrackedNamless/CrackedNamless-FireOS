package com.crackednamless.fireos;

import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    public interface OnLaunch {
        void launch(AppEntry entry);
    }

    private static final int TYPE_APP = 0;
    private static final int TYPE_ALL = 1;

    private final PackageManager pm;
    private final OnLaunch onLaunch;
    private final Runnable onAll;
    private final List<AppEntry> items = new ArrayList<>();

    public AppAdapter(PackageManager pm, OnLaunch onLaunch, Runnable onAll) {
        this.pm = pm;
        this.onLaunch = onLaunch;
        this.onAll = onAll;
    }

    public void submit(List<AppEntry> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_APP : TYPE_ALL;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(Holder h, int position) {
        FocusScale.apply(h.itemView, null);
        if (getItemViewType(position) == TYPE_ALL) {
            h.label.setText(R.string.all_short);
            h.image.setImageResource(R.drawable.ic_grid);
            h.itemView.setOnClickListener(v -> onAll.run());
            return;
        }
        AppEntry e = items.get(position);
        h.label.setText(e.label);
        h.image.setImageDrawable(AppArt.icon(pm, h.image.getResources(), e));
        h.itemView.setOnClickListener(v -> onLaunch.launch(e));
    }

    @Override
    public int getItemCount() {
        return onAll != null && !items.isEmpty() ? items.size() + 1 : items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView label;

        Holder(View v) {
            super(v);
            image = v.findViewById(R.id.tile_image);
            label = v.findViewById(R.id.tile_label);
        }
    }
}
