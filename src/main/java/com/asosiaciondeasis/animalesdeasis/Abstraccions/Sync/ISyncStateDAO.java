package com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync;

/** Where an installation remembers how far it has read the shared copy. */
public interface ISyncStateDAO {

    /** @return what was last saved, or {@link SyncState#none()} on a first run */
    SyncState load() throws Exception;

    void save(SyncState state) throws Exception;
}
