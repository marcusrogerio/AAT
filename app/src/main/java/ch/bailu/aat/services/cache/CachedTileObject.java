package ch.bailu.aat.services.cache;

import android.content.Context;
import android.graphics.Bitmap;

import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Tile;

import java.io.OutputStream;

import ch.bailu.aat.map.tile.source.CacheOnlySource;
import ch.bailu.aat.map.tile.source.Source;
import ch.bailu.aat.services.ServiceContext;
import ch.bailu.aat.services.background.FileHandle;
import ch.bailu.aat.util.AppBroadcaster;
import ch.bailu.aat.util.fs.foc.FocAndroid;
import ch.bailu.aat.util.ui.AppLog;
import ch.bailu.util_java.foc.Foc;

public class CachedTileObject extends TileObject {
    private final static int MIN_SAVE_ZOOM_LEVEL = 16;

    private final Tile mapTile;

    private final ObjectHandle.Factory cachedFactory, sourceFactory;
    private final String cachedID, sourceID;

    private TileObject tile = null;

    private final FileHandle save;

    private final Foc cachedImageFile;

    public CachedTileObject(String id, final ServiceContext sc,  Tile t, Source source) {
        super(id);

        mapTile = t;

        sourceID = source.getID(t, sc.getContext());
        sourceFactory = source.getFactory(t);

        final Source cached = new CacheOnlySource(source);

        cachedID = cached.getID(t, sc.getContext());
        cachedFactory = cached.getFactory(t);

        cachedImageFile = FocAndroid.factory(sc.getContext(), cachedID);

        save = new FileHandle(cachedImageFile) {

            @Override
            public long bgOnProcess(ServiceContext sc) {
                long size = 0;

                if (sc.lock()) {
                    size = save(sc);

                    sc.free();
                }


                return size;
            }


            private long save(ServiceContext sc) {
                long size = 0;

                ObjectHandle handle = sc.getCacheService().getObject(sourceID);

                if (handle instanceof TileObject) {
                    TileObject self = (TileObject) handle;

                    size = save(sc, self);
                }

                handle.free();
                return size;
            }


            private long save(ServiceContext sc, TileObject self) {
                long size = 0;
                OutputStream out = null;
                Foc file = cachedImageFile;

                if (file.exists() == false) {
                    try {

                        out = file.openW();

                        Bitmap bitmap = self.getBitmap();
                        if (bitmap != null && out != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 0, out);
                        }

                        AppBroadcaster.broadcast(sc.getContext(), AppBroadcaster.FILE_CHANGED_ONDISK,
                                cachedID, sourceID);

                        size = self.getSize();

                    } catch (Exception e) {
                        AppLog.d(this, e.toString());


                    } finally {
                        Foc.close(out);

                    }
                }

                return size;
            }


        };
    }

    @Override
    public void onInsert(ServiceContext sc) {
        if (isLoadable(sc.getContext())) {
            tile = (TileObject) sc.getCacheService().getObject(cachedID, cachedFactory);
        } else {
            tile = (TileObject) sc.getCacheService().getObject(sourceID, sourceFactory);
        }
        sc.getCacheService().addToBroadcaster(this);
    }

    private boolean isLoadable(Context c) {
        return cachedImageFile.exists();
    }




    @Override
    public void onChanged(String id, ServiceContext sc) {
        if (id.equals(tile.toString())) {
            AppBroadcaster.broadcast(sc.getContext(),
                    AppBroadcaster.FILE_CHANGED_INCACHE,
                    toString());



            if (
                    mapTile.zoomLevel <= MIN_SAVE_ZOOM_LEVEL &&
                            id.equals(sourceID) &&
                            tile.isLoaded()) {

                sc.getBackgroundService().process(save);
            }
        }
    }


    @Override
    public void onRemove(ServiceContext cs) {
        tile.free();
    }


    @Override
    public Bitmap getBitmap() {
        if (tile != null) return tile.getBitmap();
        return null;
    }

    @Override
    public TileBitmap getTileBitmap() {
        if (tile != null) return tile.getTileBitmap();
        return null;
    }

    @Override
    public Tile getTile() {
        return mapTile;
    }

    @Override
    public void reDownload(ServiceContext sc) {
        cachedImageFile.rm();

        tile.free();
        tile = (TileObject) sc.getCacheService().getObject(sourceID, sourceFactory);
    }

    @Override
    public boolean isLoaded() {
        return (tile != null && tile.isLoaded());
    }


    @Override
    public long getSize() {
        if (tile != null) return tile.getSize();
        return MIN_SIZE;
    }

    @Override
    public void onDownloaded(String id, String url, ServiceContext sc) {}


    @Override
    public Foc getFile() {
        return cachedImageFile;
    }


    public static class Factory extends ObjectHandle.Factory {
        private final Source source;
        private final Tile tile;

        public Factory(Tile t, Source s) {
            source = s;
            tile = t;
        }

        @Override
        public ObjectHandle factory(String id, ServiceContext cs) {
            return new CachedTileObject(id, cs, tile, source);
        }
    }


}
