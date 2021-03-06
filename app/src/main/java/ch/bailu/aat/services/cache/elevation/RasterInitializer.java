package ch.bailu.aat.services.cache.elevation;

import ch.bailu.aat.services.ServiceContext;
import ch.bailu.aat.services.background.ProcessHandle;
import ch.bailu.aat.services.cache.ObjectHandle;
import ch.bailu.aat.util.AppBroadcaster;

public class RasterInitializer extends ProcessHandle {

    private final String iid;


    public RasterInitializer(String i) {
        iid = i;
    }

    @Override
    public long bgOnProcess(ServiceContext sc) {
        long size = 0;

        if (sc.lock()) {
            ObjectHandle handle = sc.getCacheService().getObject(iid);

            if (handle instanceof ElevationTile) {
                ElevationTile owner = (ElevationTile) handle;
                size = owner.bgOnProcessInitializer(sc);
            }

            handle.free();
            sc.free();
        }
        return size;
    }
}



