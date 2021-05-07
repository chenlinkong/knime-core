package org.knime.core.data.v2.value.cell;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataCellDataInput;
import org.knime.core.data.DataCellDataOutput;
import org.knime.core.data.container.BlobDataCell;
import org.knime.core.data.container.LongUTFDataInputStream;
import org.knime.core.data.container.LongUTFDataOutputStream;
import org.knime.core.data.filestore.FileStore;
import org.knime.core.data.filestore.FileStoreCell;
import org.knime.core.data.filestore.FileStoreUtil;
import org.knime.core.data.v2.DataCellSerializerFactory.DataCellSerializerInfo;

/**
 * @author Christian Dietz, KNIME GmbH, Grunbach, Germany
 */
final class WrappedBlobDataCell extends FileStoreCell {

    private static final long serialVersionUID = 1L;

    private final static BlobLRUCache CACHE = new BlobLRUCache();

    private DataCell m_cell;

    private DataCellSerializerInfo m_info;

    public WrappedBlobDataCell(final DataCellSerializerInfo info) {
        super();
        m_info = info;
    }

    public WrappedBlobDataCell(final DataCell cell, final DataCellSerializerInfo info, final FileStore store) {
        super(store);
        m_info = info;
        m_cell = cell;
    }

    @Override
    protected void flushToFileStore() throws IOException {
        final File file = getFileStores()[0].getFile();
        // has been written already...
        if (file.length() == 0) {
            try (final FileBasedDataCellDataOutput in = new FileBasedDataCellDataOutput(file)) {
                m_info.getSerializer().serialize(m_cell, in);
            }
        }
    }

    // TODO Urks
    DataCell getDataCell() throws IOException {
        FileStore store = getFileStores()[0];
        synchronized (m_info) {
            if (m_cell == null) {
                final SoftReference<BlobDataCell> ref =
                    CACHE.get(FileStoreUtil.getFileStoreKey(getFileStores()[0]).getName());
                if (ref != null) {
                    BlobDataCell cell = ref.get();
                    if (cell != null) {
                        return cell;
                    }
                }
                try (final FileBasedDataCellDataInput in = new FileBasedDataCellDataInput(store.getFile())) {
                    return m_info.getSerializer().deserialize(in);
                }
            } else {
                return m_cell;
            }
        }
    }

    /** Last recently used cache for blobs. */
    static final class BlobLRUCache extends LinkedHashMap<String, SoftReference<BlobDataCell>> {

        private static final long serialVersionUID = 1L;

        /** Default constructor, instructs for access order. */
        BlobLRUCache() {
            super(16, 0.75f, true); // args copied from HashMap implementation
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean removeEldestEntry(final java.util.Map.Entry<String, SoftReference<BlobDataCell>> eldest) {
            return size() >= 100;
        }

        /** {@inheritDoc} */
        @Override
        public synchronized SoftReference<BlobDataCell> get(final Object key) {
            return super.get(key);
        }

        /** {@inheritDoc} */
        @Override
        public synchronized SoftReference<BlobDataCell> put(final String key, final SoftReference<BlobDataCell> value) {
            return super.put(key, value);
        }

    }

    static final class FileBasedDataCellDataInput extends LongUTFDataInputStream implements DataCellDataInput {

        @SuppressWarnings("resource")
        public FileBasedDataCellDataInput(final File f) throws FileNotFoundException {
            super(new DataInputStream(new BufferedInputStream(new FileInputStream(f))));
        }

        @Override
        public DataCell readDataCell() throws IOException {
            throw new UnsupportedOperationException("Not implemented.");
        }

    }

    static final class FileBasedDataCellDataOutput extends LongUTFDataOutputStream implements DataCellDataOutput {

        @SuppressWarnings("resource")
        public FileBasedDataCellDataOutput(final File f) throws FileNotFoundException {
            super(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f))));
        }

        @Override
        public void writeDataCell(final DataCell cell) throws IOException {
            throw new UnsupportedOperationException("Not implemented.");
        }

    }
}