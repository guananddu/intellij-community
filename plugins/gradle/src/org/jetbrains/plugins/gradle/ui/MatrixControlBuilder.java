package org.jetbrains.plugins.gradle.ui;

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;

/**
 * Allows to build control that shows target matrix-like information.
 * <p/>
 * <code>'Matrix-like'</code> here means that there is a set of columns and a number of rows where every row contains values for
 * every column.
 * <p/>
 * Example:
 * <pre>
 *            | Column1 name   |   Column2 name
 *  ---------------------------|-----------------
 *  Row1 name |   Value11      |     Value12
 *  ---------------------------|-----------------
 *  Row2 name |   Value21      |     Value22
 *  ---------------------------|-----------------
 * </pre>
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/14/12 3:52 PM
 */
public class MatrixControlBuilder {
  
  private final DefaultTableModel myModel = new DefaultTableModel();
  private final JComponent result;

  public MatrixControlBuilder(@NotNull String ... columns) {
    myModel.addColumn(""); // Row name
    for (String column : columns) {
      myModel.addColumn(column);
    }
    final JBTable table = new JBTable(myModel) {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
      }
    };
    table.setStriped(true);
    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
    renderer.setHorizontalAlignment(SwingConstants.CENTER);
    for (int i = 1/* don't align row name */, max = table.getColumnCount(); i < max; i++) {
      table.getColumnModel().getColumn(i).setCellRenderer(renderer);
    }
    //table.setDefaultRenderer(String.class, renderer);
    result = ScrollPaneFactory.createScrollPane(table);
  }
  
  /**
   * Supplies current builder within the information to use during the new row representation.
   * 
   * @param name    new row now
   * @param values  new row values
   * @throws IllegalArgumentException   if given row values imply number of columns that differs from the number of already configured one
   */
  public void addRow(@NotNull String name, @NotNull Object... values) throws IllegalArgumentException {
    if (values.length != myModel.getColumnCount() - 1) {
      StringBuilder columns = new StringBuilder();
      for (int i = 1, max = myModel.getColumnCount(); i < max; i++) {
        columns.append(myModel.getColumnName(i)).append(", ");
      }
      if (columns.length() > 2) {
        columns.setLength(columns.length() - 2);
      }
      throw new IllegalArgumentException(String.format(
        "Can't add row '%s' to the matrix control. Reason: the row specifies incorrect number of values (%d, expected %d). "
        + "Registered columns: %s. Given values: %s", name, values.length, myModel.getColumnCount() - 1, columns, Arrays.toString(values)
        ));
    }
    Object[] rowData = new Object[values.length + 1];
    rowData[0] = name;
    System.arraycopy(values, 0, rowData, 1, values.length);
    myModel.addRow(rowData);
  }
  
  @NotNull
  public JComponent build() {
    return result;
  }
}
