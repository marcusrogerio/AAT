package ch.bailu.aat.services.dem.loader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.Closeable;

import ch.bailu.aat.coordinates.SrtmCoordinates;
import ch.bailu.aat.services.ServiceContext;
import ch.bailu.aat.services.background.DownloadHandle;
import ch.bailu.aat.services.dem.tile.Dem3Tile;
import ch.bailu.aat.util.AppBroadcaster;
import ch.bailu.aat.util.AppIntent;
import ch.bailu.aat.util.Timer;
import ch.bailu.aat.util.ui.AppLog;
import ch.bailu.util_java.foc.Foc;

public class Dem3TileLoader implements Closeable {
    private static final long MILLIS=2000;

    private final ServiceContext scontext;
    private final Dem3Tiles tiles;

    private SrtmCoordinates pending = null;



    public Dem3TileLoader(ServiceContext sc, Dem3Tiles t) {
        tiles = t;
        scontext = sc;

        AppBroadcaster.register(sc.getContext(), onFileDownloaded, AppBroadcaster.FILE_CHANGED_ONDISK);

    }


    private final Timer timer = new Timer(new Runnable() {
        @Override
        public void run() {
            if (havePending()) {
                loadOrDownloadPending();
                stopTimer();
            }
        }
    }, MILLIS);


    private final BroadcastReceiver onFileDownloaded = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String id = AppIntent.getFile(intent);
            Dem3Tile tile = tiles.get(id);

            if (tile != null) {
                tile.reload(scontext);
            }
        }
    };


    private void loadOrDownloadPending() {
        final SrtmCoordinates loadNow = pending;
        pending = null;

        if (loadNow != null) {
            loadNow(loadNow);
            downloadNow(loadNow);
        }
    }


    public Dem3Tile loadNow(SrtmCoordinates c) {
        if (havePending()) cancelPending();
        return loadIntoOldestSlot(c);
    }

    private Dem3Tile loadIntoOldestSlot(SrtmCoordinates c) {
        if (tiles.have(c) == false) {
            final Dem3Tile slot = tiles.getOldestProcessed();

            if (slot == null) {
                //AppLog.d(this, "not loading because no slot");
            } else {
                if (slot.isLocked()) {
                    //AppLog.d(this, "not loading because slot is locked");
                }
                slot.load(scontext, c);
            }
        }

        return tiles.get(c);
    }



    public void loadOrDownloadLater(SrtmCoordinates c) {
        if (pending == null) { // first BitmapRequest
            startTimer();
        }
        pending = c;
    }


    public void cancelPending() {
        pending = null;
        stopTimer();
    }

    private boolean havePending() {
        return pending != null;
    }


    private void startTimer() {
        timer.close();
        timer.kick();
    }

    private void stopTimer() {
        timer.close();
    }



    private void downloadNow(SrtmCoordinates c) {
        Foc file = c.toFile(scontext.getContext());
        if (!file.exists()) {
            DownloadHandle handle = new DownloadHandle(c.toURL(), file);
            scontext.getBackgroundService().download(handle);
        }
    }


    @Override
    public void close() {
        scontext.getContext().unregisterReceiver(onFileDownloaded);
    }
}
