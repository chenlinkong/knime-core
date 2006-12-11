/*
 * --------------------------------------------------------------------- *
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2006
 * University of Konstanz, Germany.
 * Chair for Bioinformatics and Information Mining
 * Prof. Dr. Michael R. Berthold
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * --------------------------------------------------------------------- *
 */
package org.knime.base.node.mine.scorer.hilitescorer;

import java.awt.Point;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.knime.base.node.util.DataArray;
import org.knime.base.node.viz.plotter.DataProvider;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.container.DataContainer;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * The hilite scorer node's model. The scoring is performed on two given columns
 * set by the dialog. The row keys are stored for later hiliting purpose.
 * 
 * @author Christoph Sieb, University of Konstanz
 * 
 * @see HiliteScorerNodeFactory
 */
public class HiliteScorerNodeModel extends NodeModel implements DataProvider {
    /** The node logger fot this class. */

    protected static final NodeLogger LOGGER = NodeLogger
            .getLogger(HiliteScorerNodeModel.class);

    /** Identifier in model spec to address first column name to compare. */
    static final String FIRST_COMP_ID = "first";

    /** Identifier in model spec to address first second name to compare. */
    static final String SECOND_COMP_ID = "second";

    /** The input port 0. */
    static final int INPORT = 0;

    /** The output port 0. */
    static final int OUTPORT = 0;

    /** The name of the first column to compare. */
    private String m_firstCompareColumn;

    /** The name of the second column to compare. */

    private String m_secondCompareColumn;

    /** Counter for correct classification, set in execute. */
    private int m_correctCount;

    /** Counter for misclassification, set in execute. */
    private int m_falseCount;

    private BitSet m_rocCurve;
    
    /**
     * Stores the row keys for the confusion matrix fields to allow hiliting.
     */
    private List<DataCell>[][] m_keyStore;

    /**
     * The confusion matrix as int 2-D array. Note: m_lastResult contains the
     * same data as DataTable which is put to the output port.
     */
    private int[][] m_scorerCount;

    /**
     * The attribute names of the confution matrix.
     */
    private String[] m_values;

    /**
     * Number of rows in the input table. Interesting if you want to know the
     * number of missing values in either of the target columns.
     */
    private int m_nrRows;

    /** holds the last scorer table for the view. */
    private DataTable m_lastResult;

    /** Inits a new <code>ScorerNodeModel</code> with one in- and one output. */
    HiliteScorerNodeModel() {
        super(1, 1);
        m_lastResult = null;
    }

    /**
     * Starts the scoring in the scorer.
     * 
     * @param data the input data of length one
     * @param exec the execution monitor
     * @return the confusion matrix
     * @throws CanceledExecutionException if user canceled execution
     * 
     * @see NodeModel#execute(BufferedDataTable[],ExecutionContext)
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] data,
            final ExecutionContext exec) throws CanceledExecutionException {
        // check input data
        assert (data != null && data.length == 1 && data[INPORT] != null);
        // blow away result from last execute (should have been reset anyway)
        m_lastResult = null;
        // first try to figure out what are the different class values
        // in the two respective columns
        BufferedDataTable in = data[INPORT];
        DataTableSpec inSpec = in.getDataTableSpec();
        final int index1 = inSpec.findColumnIndex(m_firstCompareColumn);
        final int index2 = inSpec.findColumnIndex(m_secondCompareColumn);

        // two elements, first is column names, second row names;
        // these arrays are ordered already, i.e. if both columns have
        // cells in common (e.g. both have Iris-Setosa), they get the same
        // index in the array. thus, the high numbers should appear
        // in the diagonal
        m_values = determineColValues(in, index1, index2, exec);
        List<String> valuesList = Arrays.asList(m_values);

        m_correctCount = 0;
        m_falseCount = 0;

        // the key store remembers the row key for later hiliting
        m_keyStore = new List[m_values.length][m_values.length];
        // the scorerCount counts the confusions
        m_scorerCount = new int[m_values.length][m_values.length];
        
        m_rocCurve = new BitSet(in.getRowCount() + 1);

        // init the matrix
        for (int i = 0; i < m_keyStore.length; i++) {
            for (int j = 0; j < m_keyStore[i].length; j++) {
                m_keyStore[i][j] = new ArrayList<DataCell>();
            }
        }

        int rowNr = 0;
        for (Iterator<DataRow> it = in.iterator(); it.hasNext(); rowNr++) {
            DataRow row = it.next();
            exec.setProgress(rowNr / (double)in.getRowCount(),
                    "Computing score, row " + rowNr + " (\"" + row.getKey()
                            + "\") of " + in.getRowCount());
            try {
                exec.checkCanceled();
            } catch (CanceledExecutionException cee) {
                reset();
                throw cee;
            }
            DataCell cell1 = row.getCell(index1);
            DataCell cell2 = row.getCell(index2);
            if (cell1.isMissing() || cell2.isMissing()) {
                continue;
            }
            boolean areEqual = cell1.equals(cell2);
            
            m_rocCurve.set(rowNr, areEqual);
            
            // need to cast to string (column keys are strings!)
            int i1 = valuesList.indexOf(cell1.toString());
            int i2 = areEqual ? i1 : valuesList.indexOf(cell2.toString());
            assert i1 >= 0 : "column spec lacks possible value " + cell1;
            assert i2 >= 0 : "column spec lacks possible value " + cell2;
            // i2 must be equal to i1 if cells are equal (implication)
            assert (!areEqual || i1 == valuesList.indexOf(cell2.toString()));
            m_keyStore[i1][i2].add(row.getKey().getId());
            m_scorerCount[i1][i2]++;

            if (areEqual) {
                m_correctCount++;
            } else {
                m_falseCount++;
            }
        }
        // set the last bit to indicate the end of the set
        m_rocCurve.set(in.getRowCount());

        m_nrRows = rowNr;
        DataType[] colTypes = new DataType[m_values.length];
        Arrays.fill(colTypes, IntCell.TYPE);
        BufferedDataContainer container =
                exec.createDataContainer(new DataTableSpec(m_values, colTypes));
        for (int i = 0; i < m_values.length; i++) {
            // need to make a datacell for the row key
            container.addRowToTable(new DefaultRow(new StringCell(m_values[i]),
                    m_scorerCount[i]));
        }
        container.close();

        // print info
        int correct = getCorrectCount();
        int incorrect = getFalseCount();
        double error = getError();
        int nrRows = getNrRows();
        int missing = nrRows - correct - incorrect;
        LOGGER.info("error=" + error + ", #correct=" + correct + ", #false="
                + incorrect + ", #rows=" + nrRows + ", #missing=" + missing);
        // our view displays the table - we must keep a reference in the model.
        m_lastResult = container.getTable();
        return new BufferedDataTable[]{exec.createBufferedDataTable(
                m_lastResult, exec)};

    } // execute(DataTable[],ExecutionMonitor)

    /**
     * Resets all internal data.
     */
    @Override
    protected void reset() {
        m_correctCount = -1;
        m_falseCount = -1;
        m_lastResult = null;
        m_keyStore = null;
        m_rocCurve = null;
    }

    /**
     * @return the result of the last run. Will be <code>null</code> before an
     *         execute and after a reset.
     */
    DataTable getScorerTable() {
        return m_lastResult;
    }

    /**
     * Sets the columns that will be compared during execution.
     * 
     * @param first the first column
     * @param second the second column
     * @throws NullPointerException if one of the parameters is
     *             <code>null</code>
     */
    void setCompareColumn(final String first, final String second) {
        if ((first == null || second == null)) {
            throw new NullPointerException("Must specify both columns to"
                    + " compare!");
        }
        m_firstCompareColumn = first;
        m_secondCompareColumn = second;
    }

    /**
     * @see NodeModel#configure(DataTableSpec[])
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        if (inSpecs[INPORT].getNumColumns() < 2) {
            throw new InvalidSettingsException(
                    "The input table must have at least two colums to compare");
        }
        if ((m_firstCompareColumn == null) || (m_secondCompareColumn == null)) {
            throw new InvalidSettingsException("No columns selected yet.");
        }
        if (!inSpecs[INPORT].containsName(m_firstCompareColumn)) {
            throw new InvalidSettingsException("Column " + m_firstCompareColumn
                    + " not found.");
        }
        if (!inSpecs[INPORT].containsName(m_secondCompareColumn)) {
            throw new InvalidSettingsException("Column "
                    + m_secondCompareColumn + " not found.");
        }
        return new DataTableSpec[1];
    }

    /**
     * Get the correct classification count, i.e. where both columns agree.
     * 
     * @return the count of rows where the two columns have an equal value or -1
     *         if the node is not executed
     */
    public int getCorrectCount() {
        return m_correctCount;
    }

    /**
     * Get the misclassification count, i.e. where both columns have different
     * values.
     * 
     * @return the count of rows where the two columns have an unequal value or
     *         -1 if the node is not executed
     */
    public int getFalseCount() {
        return m_falseCount;
    }

    /**
     * Get the number of rows in the input table. This count can be different
     * from {@link #getFalseCount()} + {@link #getCorrectCount()}, though it
     * must be at least the sum of both. The difference is the number of rows
     * containing a missing value in either of the target columns.
     * 
     * @return number of rows in input table
     */
    public int getNrRows() {
        return m_nrRows;
    }

    /**
     * Returns the error of wrong classfied pattern in percentage of the number
     * of patterns.
     * 
     * @return the 1.0 - classification accuracy
     */
    public float getError() {
        float error;
        long totalNumberDataSets = getFalseCount() + getCorrectCount();
        if (totalNumberDataSets == 0) {
            error = Float.NaN;
        } else {
            float ratio = 100.0f / totalNumberDataSets;
            error = ratio * getFalseCount();
        }
        return error;
    }

    /**
     * @see NodeModel#loadValidatedSettingsFrom(NodeSettingsRO)
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        String col1 = settings.getString(FIRST_COMP_ID);
        String col2 = settings.getString(SECOND_COMP_ID);
        setCompareColumn(col1, col2);
    }

    /**
     * @see NodeModel#saveSettingsTo(NodeSettingsWO)
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        if (m_firstCompareColumn != null) {
            settings.addString(FIRST_COMP_ID, m_firstCompareColumn);
        }
        if (m_secondCompareColumn != null) {
            settings.addString(SECOND_COMP_ID, m_secondCompareColumn);
        }
    }

    /**
     * @see NodeModel#validateSettings(NodeSettingsRO)
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        settings.getString(FIRST_COMP_ID);
        settings.getString(SECOND_COMP_ID);
    }

    /**
     * Determines the row keys (as DataCells) which belong to the given cell of
     * the confution matrix.
     * 
     * @param cells the cells of the confusion matrix for which the keys should
     *            be returned
     * 
     * @return a set of DataCells containing the row keys
     */
    Set<DataCell> getSelectedSet(final Point[] cells) {

        Set<DataCell> keySet = new HashSet<DataCell>();

        for (Point cell : cells) {

            int xVal = cell.x;
            int yVal = cell.y;

            // if the specified x and y values of the matrix are out of range
            // continue
            if (xVal < 0 || yVal < 0 || xVal > m_values.length
                    || yVal > m_values.length) {
                continue;
            }

            keySet.addAll(m_keyStore[xVal][yVal]);
        }

        return keySet;
    }

    /**
     * Called to determine all possible values in the respective columns. This
     * method will "stringify" all values as they show up as row and column key.
     * 
     * @param in the input table
     * @param index1 the first column to compare
     * @param index2 the second column to compare
     * @param exec object to check with if user canceled
     * @return the order of rows and columns in the confusion matrix
     * @throws CanceledExecutionException if user canceled operation
     */
    protected String[] determineColValues(final DataTable in, final int index1,
            final int index2, final ExecutionMonitor exec)
            throws CanceledExecutionException {

        DataTableSpec inSpec = in.getDataTableSpec();
        DataColumnSpec col1 = inSpec.getColumnSpec(index1);
        DataColumnSpec col2 = inSpec.getColumnSpec(index2);
        Set<DataCell> v1 = col1.getDomain().getValues();
        Set<DataCell> v2 = col2.getDomain().getValues();
        LinkedHashSet<DataCell> values1;
        LinkedHashSet<DataCell> values2;
        if (v1 == null || v2 == null) { // values not available
            values1 = new LinkedHashSet<DataCell>();
            values2 = new LinkedHashSet<DataCell>();
            int rowNr = 0;
            for (Iterator<DataRow> it = in.iterator(); it.hasNext(); rowNr++) {
                DataRow row = it.next();
                exec.checkCanceled(); // throws excepton if user canceled.
                exec.setMessage("Reading possible values, row " + rowNr
                        + " (\"" + row.getKey() + "\")");
                DataCell cell1 = row.getCell(index1);
                DataCell cell2 = row.getCell(index2);
                values1.add(cell1);
                values2.add(cell2);
            }
        } else {
            // clone them, will change the set later on
            values1 = new LinkedHashSet<DataCell>(v1);
            values2 = new LinkedHashSet<DataCell>(v2);
        }
        // number of all possible values in both column (number of rows
        // and columns in the confusion matrix)
        HashSet<DataCell> union = new HashSet<DataCell>(values1);
        union.addAll(values2);
        int unionCount = union.size();

        // intersection of values in columns
        LinkedHashSet<DataCell> intersection = new LinkedHashSet<DataCell>(
                values1);
        intersection.retainAll(values2);

        // an array of the intersection set
        DataCell[] intSecAr = intersection.toArray(new DataCell[0]);

        // order the elements (classes that occur in both columns first)
        // a good scoring is when the elements in the diagonal are high
        DataCell[] order = new DataCell[unionCount];
        System.arraycopy(intSecAr, 0, order, 0, intSecAr.length);

        // copy all values that are in values1 but not in values2
        values1.removeAll(intersection);
        DataCell[] temp = values1.toArray(new DataCell[0]);
        System.arraycopy(temp, 0, order, intSecAr.length, temp.length);

        // copy all values that are in values2 but not in values1
        values2.removeAll(intersection);
        temp = values2.toArray(new DataCell[0]);
        System.arraycopy(temp, 0, order, order.length - temp.length,
                temp.length);
        // make them a string
        String[] result = new String[order.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = order[i].toString();
        }
        return result;
    } // determineColValues(DataTable, int, int)

    /**
     * Finds the position where key is located in source. It must be ensured
     * that the key is indeed in the argument array.
     * 
     * @param source the source array
     * @param key the key to find
     * @return the index in source where key is located
     */
    protected static int findValue(final DataCell[] source, 
            final DataCell key) {
        for (int i = 0; i < source.length; i++) {
            if (source[i].equals(key)) {
                return i;
            }
        }
        throw new RuntimeException("You should never come here.");
    }

    /**
     * Checks if the specified confusion matrix cell contains at least one of
     * the given keys.
     * 
     * @param x the x value to specify the matrix cell
     * @param y the y value to specify the matrix cell
     * @param keys the keys to check
     * 
     * @return true if at least one key is contained in the specified cell
     */
    boolean containsConfusionMatrixKeys(final int x, final int y,
            final Set<DataCell> keys) {

        // get the list with the keys
        List<DataCell> keyList = m_keyStore[x][y];
        for (DataCell key : keyList) {
            for (DataCell keyToCheck : keys) {
                if (key.equals(keyToCheck)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns all cells of the confusion matrix (as Points) if the given key
     * set contains all keys of that cell.
     * 
     * @param keys the keys to check for
     * 
     * @return the cells that fullfill the above condition
     */
    Point[] getCompleteHilitedCells(final Set<DataCell> keys) {

        Vector<Point> result = new Vector<Point>();

        // for all cells of the matrix
        for (int i = 0; i < m_keyStore.length; i++) {
            for (int j = 0; j < m_keyStore[i].length; j++) {
                // for all keys to check
                boolean allKeysIncluded = true;
                

                for (DataCell key : m_keyStore[i][j]) {

                    boolean wasKeyFound = false;
                    Iterator<DataCell> keysToCheckIterator = keys.iterator();
                    while (keysToCheckIterator.hasNext()) {

                        DataCell keyToCheck = keysToCheckIterator.next();

                        if (key.equals(keyToCheck)) {
                            // if the keys equal remove it, as it can only
                            // occure in one cell
                            //keysToCheckIterator.remove();

                            // remember that the key was found
                            wasKeyFound = true;
                        }
                    }

                    if (!wasKeyFound) {
                        // if one key was not found the cell is not repesented
                        // completely by "keys" (the keys to check
                        allKeysIncluded = false;
                    }
                }

                if (allKeysIncluded && m_keyStore[i][j].size() > 0) {
                    result.add(new Point(i, j));
                }
            }
        }
        
        return result.toArray(new Point[result.size()]);
    }

    /**
     * @see org.knime.core.node.NodeModel
     *      #loadInternals(java.io.File,ExecutionMonitor)
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException {
        // TODO: load last result and internal variables to restore
        // view content (see reset())

        File f = new File(internDir, "data.ser");
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(
                new FileInputStream(f)));
        try {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> hm =
                (HashMap<String, Object>)in.readObject();
            m_correctCount = (Integer)hm.get("correctCount");
            m_falseCount = (Integer)hm.get("falseCount");
            m_keyStore = (List<DataCell>[][])hm.get("keyStore");
            m_nrRows = (Integer) hm.get("nrRows");
            m_rocCurve = (BitSet)hm.get("rocCurve");
            m_scorerCount = (int[][])hm.get("scorerCount");
            m_values = (String[])hm.get("values");
        } catch (ClassNotFoundException ex) {         
            // should not happen at all
            LOGGER.error("Could not read internals", ex);
        }
        in.close();
        
        f = new File(internDir, "lastResult.tab.zip");
        m_lastResult = DataContainer.readFromZip(f);
    }

    /**
     * @see org.knime.core.node.NodeModel
     *      #saveInternals(java.io.File,ExecutionMonitor)
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException {
        // TODO: load last result and internal variables to restore
        // view content (see reset())

        HashMap<String, Object> hs = new HashMap<String, Object>();
        hs.put("correctCount", m_correctCount);
        hs.put("falseCount", m_falseCount);
        hs.put("keyStore", m_keyStore);
        hs.put("nrRows", m_nrRows);
        hs.put("rocCurve", m_rocCurve);
        hs.put("scorerCount", m_scorerCount);
        hs.put("values", m_values);

        ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(
                new File(internDir, "data.ser"))));
        out.writeObject(hs);
        out.close();


        if (m_lastResult != null) {
            File f = new File(internDir, "lastResult.tab.zip");
            try {
                DataContainer.writeToZip(m_lastResult, f, exec);
            } catch (CanceledExecutionException ex) {
                // TODO Auto-generated catch block
                ex.printStackTrace();
            }
        }
    }

    /**
     * @return the confusion matrix as int 2-D array
     */
    int[][] getScorerCount() {
        return m_scorerCount;
    }

    /**
     * Returns a bit set with data for the ROC curve. A set bit means a correct
     * classified example, an unset bit is a wrong classified example. The
     * number of interesting bits is {@link BitSet#length()} - 1, i.e. the last
     * set bit must be ignored, it is just the end marker. 
     * 
     * @return a bit set
     */
    BitSet getRocCurve() {
        return m_rocCurve;
    }
        
    /**
     * @return the attribute names of the confusion matrix
     */
    String[] getValues() {
        return m_values;
    }

    /**
     * @see org.knime.base.node.viz.plotter.DataProvider
     *  #getDataArray(int)
     */
    public DataArray getDataArray(final int index) {
        // only to make the Plotter happy
        return null;
    }
}
