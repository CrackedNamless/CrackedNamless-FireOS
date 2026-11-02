package com.crackednamless.fireos;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    public interface OnLaunch {
        void launch(AppEntry entry);
    }

    public interface OnFolder {
        void open(String name);
    }

    public interface OnMenu {
        void menu(Tile tile);
    }

    // Una card: un'app oppure una cartella con le sue app.
    public static final class Tile {
        final AppEntry app;
        final String folder;
        final List<AppEntry> members;

        private Tile(AppEntry app, String folder, List<AppEntry> members) {
            this.app = app;
            this.folder = folder;
            this.members = members;
        }

        static Tile of(AppEntry app) {
            return new Tile(app, null, null);
        }

        static Tile folder(String name, List<AppEntry> members) {
            return new Tile(null, name, members);
        }

        String key() {
            return app != null ? app.packageName + "/" + app.className : "folder:" + folder;
        }
    }

    private static final int TYPE_TILE = 0;
    private static final int TYPE_ALL = 1;

    private final PackageManager pm;
    private final OnLaunch onLaunch;
    private final Runnable onAll;
    private final OnFolder onFolder;
    private final OnMenu onMenu;
    private final List<Tile> items = new ArrayList<>();

    public AppAdapter(PackageManager pm, OnLaunch onLaunch, Runnable onAll, OnFolder onFolder, OnMenu onMenu) {
        this.pm = pm;
        this.onLaunch = onLaunch;
        this.onAll = onAll;
        this.onFolder = onFolder;
        this.onMenu = onMenu;
    }

    public void submit(List<AppEntry> apps) {
        List<Tile> tiles = new ArrayList<>(apps.size());
        for (AppEntry e : apps) {
            tiles.add(Tile.of(e));
        }
        submitTiles(tiles);
    }

    public void submitTiles(List<Tile> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_TILE : TYPE_ALL;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        final float radius = 16 * parent.getResources().getDisplayMetrics().density;
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
        FocusScale.apply(h.itemView, null);
        Resources res = h.itemView.getResources();
        float density = res.getDisplayMetrics().density;
        if (getItemViewType(position) == TYPE_ALL) {
            h.key = "all";
            h.label.setText(R.string.all_short);
            int pad = (int) (22 * density);
            h.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            h.image.setPadding(pad, (int) (10 * density), pad, (int) (24 * density));
            h.image.setBackgroundColor(0x22FFFFFF);
            h.image.setImageResource(R.drawable.ic_grid);
            h.itemView.setOnClickListener(v -> onAll.run());
            h.itemView.setOnLongClickListener(null);
            return;
        }
        Tile tile = items.get(position);
        final String key = tile.key();
        h.key = key;
        if (onMenu != null) {
            h.itemView.setOnLongClickListener(v -> {
                onMenu.menu(tile);
                return true;
            });
            h.itemView.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean swallow = h.longPressed;
                    h.longPressed = false;
                    return swallow;
                }
                if (event.getRepeatCount() > 0 || (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                    h.longPressed = true;
                    v.setPressed(false);
                    onMenu.menu(tile);
                    return true;
                }
                h.longPressed = false;
                return false;
            });
        }
        if (tile.app == null) {
            bindFolder(h, tile, key, res);
            return;
        }
        AppEntry e = tile.app;
        h.label.setText(e.label);
        h.itemView.setOnClickListener(v -> onLaunch.launch(e));
        AppArt cached = AppArt.peek(res, e);
        if (cached != null) {
            paint(h, cached, e, density);
            return;
        }
        h.image.setImageDrawable(null);
        h.image.setPadding(0, 0, 0, 0);
        h.image.setBackgroundColor(0x22FFFFFF);
        AppArt.load(pm, res, e, art -> {
            if (key.equals(h.key)) {
                paint(h, art, e, density);
            }
        });
    }

    private void bindFolder(Holder h, Tile tile, String key, Resources res) {
        h.label.setText(res.getString(R.string.folder_tile, tile.folder, tile.members.size()));
        h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        h.image.setPadding(0, 0, 0, 0);
        h.image.setBackgroundColor(0x22FFFFFF);
        h.image.setImageDrawable(null);
        h.itemView.setOnClickListener(v -> onFolder.open(tile.folder));
        AppArt.folder(pm, res, tile.members, d -> {
            if (key.equals(h.key)) {
                h.image.setImageDrawable(d);
            }
        });
    }

    private static void paint(Holder h, AppArt art, AppEntry e, float density) {
        if (art.isBanner) {
            h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.image.setPadding(0, 0, 0, 0);
            h.image.setBackground(null);
        } else {
            int side = (int) (14 * density);
            h.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            h.image.setPadding(side, (int) (8 * density), side, (int) (22 * density));
            int accent = AppArt.cachedAccent(e);
            h.image.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{AppArt.shade(accent, 0.75f, 0.52f), AppArt.shade(accent, 0.85f, 0.26f)}));
        }
        h.image.setImageDrawable(art.drawable);
    }

    @Override
    public int getItemCount() {
        return onAll != null && !items.isEmpty() ? items.size() + 1 : items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView label;
        String key;
        boolean longPressed;

        Holder(View v) {
            super(v);
            image = v.findViewById(R.id.tile_image);
            label = v.findViewById(R.id.tile_label);
        }
    }
}
