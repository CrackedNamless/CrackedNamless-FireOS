package com.crackednamless.fireos;

import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.Holder> {

    public static final class Entry {
        final AppEntry app;
        final long time;

        public Entry(AppEntry app, long time) {
            this.app = app;
            this.time = time;
        }
    }

    private final PackageManager pm;
    private final AppAdapter.OnLaunch onLaunch;
    private final List<Entry> items = new ArrayList<>();

    public RecentAdapter(PackageManager pm, AppAdapter.OnLaunch onLaunch) {
        this.pm = pm;
        this.onLaunch = onLaunch;
    }

    public void submit(List<Entry> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent, parent, false);
        final float radius = 14 * parent.getResources().getDisplayMetrics().density;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setClipToOutline(true);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(Holder h, int position) {
        Entry e = items.get(position);
        h.label.setText(e.app.label);
        h.time.setText(h.itemView.getResources().getString(R.string.opened_ago, Recents.ago(h.itemView.getResources(), e.time, System.currentTimeMillis())));

        AppArt art = AppArt.banner(pm, h.art.getResources(), e.app);
        if (art.isBanner) {
            h.art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.art.setPadding(0, 0, 0, 0);
            h.art.setBackground(null);
        } else {
            int top = h.art.getResources().getDisplayMetrics().densityDpi / 160;
            h.art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            h.art.setPadding(0, 6 * top, 0, 26 * top);
            h.art.setBackgroundColor(AppArt.shade(AppArt.accent(art.drawable), 0.8f, 0.45f));
        }
        h.art.setImageDrawable(art.drawable);

        FocusScale.apply(h.itemView, null);
        h.itemView.setOnClickListener(v -> onLaunch.launch(e.app));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView art;
        final TextView label;
        final TextView time;

        Holder(View v) {
            super(v);
            art = v.findViewById(R.id.recent_art);
            label = v.findViewById(R.id.recent_label);
            time = v.findViewById(R.id.recent_time);
        }
    }
}
