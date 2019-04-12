package br.ufcg.spg.excel;

import br.ufcg.spg.cluster.Cluster;
import br.ufcg.spg.cluster.ClusterFormatter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import br.ufcg.spg.edit.Edit;
import br.ufcg.spg.git.GitUtils;
import br.ufcg.spg.main.Main;
import br.ufcg.spg.main.MainArguments;
import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class PoiExcelWriter {
  
  private PoiExcelWriter() {
  }
  
  /**
   * Save list of quick fixes to file.
   */
  @SuppressWarnings("deprecation")
  public static void save(List<QuickFix> qfs) 
      throws IOException {
    try {
      // create .xls and create a worksheet.
      FileOutputStream fos = new FileOutputStream("data.xls");
      HSSFWorkbook workbook = new HSSFWorkbook();
      HSSFSheet worksheet = workbook.createSheet("My Worksheet");
      for (int i = 0; i < qfs.size(); i++) {
        // Create ROW-1
        HSSFRow row1 = worksheet.createRow((short) i);
        // Create COL-A from ROW-1 and set data
        HSSFCell cellA1 = row1.createCell((short) 0);
        cellA1.setCellValue(qfs.get(i).getId());
        HSSFCellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.GOLD.index);
        // cellStyle.setFillPattern(HSSFCellStyle);
        cellA1.setCellStyle(cellStyle);
        // Create COL-B from row-1 and set data
        HSSFCell cellB1 = row1.createCell((short) 1);
        Cluster c = qfs.get(i).getCluster();
        String result = ClusterFormatter.getInstance().formatStringNodes(c.getNodes());
        String [] lines = result.split("\n");
        cellB1.setCellValue(lines[3]);
        cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellB1.setCellStyle(cellStyle);
      }
      // Save the workbook in .xls file
      workbook.write(fos);
      fos.flush();
      fos.close();
      workbook.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  /** 
   * Save clusters to excel.
   */
  @SuppressWarnings("deprecation")
  public static void save(String file, String sheet, List<QuickFix> qfs) 
      throws IOException {
    try {
      // create .xls and create a worksheet.
      FileOutputStream fos = new FileOutputStream(file);
      HSSFWorkbook workbook = new HSSFWorkbook();
      HSSFSheet worksheet = workbook.createSheet(sheet);
      for (int i = 0; i < qfs.size(); i++) {
        QuickFix qf = qfs.get(i);
        // Create ROW-1
        HSSFRow row1 = worksheet.createRow((short) i);
        // Create COL-A from ROW-1 and set data
        HSSFCell cellA1 = row1.createCell((short) 0);
        cellA1.setCellValue(qfs.get(i).getId());
        HSSFCellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.GOLD.index);
        // cellStyle.setFillPattern(HSSFCellStyle);
        cellA1.setCellStyle(cellStyle);
        // Create COL-B from row-1 and set data
        HSSFCell cellB1 = row1.createCell((short) 1);
        Cluster c = qfs.get(i).getCluster();
        String result = ClusterFormatter.getInstance().formatStringNodes(c.getNodes());
        MainArguments main = MainArguments.getInstance();
        FileUtils.writeStringToFile(new File(main.getProjectFolder() + "/cluster/" + sheet + "/" + qfs.get(i).getId() + ".txt"), result);
        String [] lines = result.split("\n");
        cellB1.setCellValue(lines[3]);
        // Create COL-C from row-i and set data
        HSSFCell cellC1 = row1.createCell((short) 2);
        cellC1.setCellValue(c.getNodes().get(0).getProject());
        cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellC1.setCellStyle(cellStyle);
        // Create COL-D from row-i and set data
        HSSFCell cellD1 = row1.createCell((short) 3);
        cellD1.setCellValue(c.getNodes().get(0).getCommit());
        cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellD1.setCellStyle(cellStyle);
        // Create COL-D from row-i and set data
        HSSFCell cellE1 = row1.createCell((short) 4);
        cellE1.setCellValue(qf.getStatus().toString());
        //cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellE1.setCellStyle(cellStyle);
      }
      // Save the workbook in .xls file
      workbook.write(fos);
      fos.flush();
      fos.close();
      workbook.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Save clusters to excel.
   */
  public static void save(String file, String sheet, String id, Cluster src, Cluster dst)
          throws IOException {
    try {
      // create .xls and create a worksheet.
      FileOutputStream fos = new FileOutputStream(file);
      HSSFWorkbook workbook = new HSSFWorkbook();
      HSSFSheet worksheet = workbook.createSheet(sheet);
      List<Edit> srcEdits = src.getNodes();
      List<Edit> dstEdits = dst.getNodes();
      for (int i = 0; i < srcEdits.size(); i++) {
        Edit srcEdit = srcEdits.get(i);
        Edit dstEdit = dstEdits.get(i);
        // Create ROW-1
        HSSFRow row1 = worksheet.createRow((short) i);
        // Create COL-A from ROW-1 and set data
        HSSFCell cellA1 = row1.createCell((short) 0);
        cellA1.setCellValue(id);
        HSSFCellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.GOLD.index);
        // cellStyle.setFillPattern(HSSFCellStyle);
        cellA1.setCellStyle(cellStyle);
        // Create COL-B from row-1 and set data
        HSSFCell cellB1 = row1.createCell((short) 1);
        //Cluster c = qfs.get(i).getCluster();
        //String result = ClusterFormatter.getInstance().formatStringNodes(c.getNodes());
        //MainArguments main = MainArguments.getInstance();
        //FileUtils.writeStringToFile(new File(main.getProjectFolder() + "/cluster/" + sheet + "/" + qfs.get(i).getId() + ".txt"), result);
        //String [] lines = result.split("\n");
        cellB1.setCellValue(srcEdit.getText());
        // Create COL-C from row-i and set data
        HSSFCell cellC1 = row1.createCell((short) 2);
        cellC1.setCellValue(dstEdit.getText());
        //cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellC1.setCellStyle(cellStyle);
        // Create COL-D from row-i and set data
        HSSFCell cellD1 = row1.createCell((short) 3);
        cellD1.setCellValue(dstEdit.getProject());
        //cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellD1.setCellStyle(cellStyle);
        // Create COL-F from row-i and set data
        HSSFCell cellE1 = row1.createCell((short) 4);
        cellE1.setCellValue(dstEdit.getCommit());
        //cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellE1.setCellStyle(cellStyle);
        // Create COL-F from row-i and set data
        HSSFCell cellF1 = row1.createCell((short) 5);
        MainArguments main = MainArguments.getInstance();
        Repository repository = GitUtils.startRepo(main.getProjectFolder() + "/" + dstEdit.getProject() + "/");
        RevCommit commit = GitUtils.extractCommit(repository, dstEdit.getCommit());
        cellF1.setCellValue(commit.getFullMessage());
        //cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        cellF1.setCellStyle(cellStyle);
      }
      // Save the workbook in .xls file
      workbook.write(fos);
      fos.flush();
      fos.close();
      workbook.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  /** 
   * Save clusters to excel.
   */
  @SuppressWarnings("deprecation")
  public static void save(String file, String sheet, Map<String, Integer> map) 
      throws IOException {
    try {
      // create .xls and create a worksheet.
      FileOutputStream fos = new FileOutputStream(file);
      HSSFWorkbook workbook = new HSSFWorkbook();
      HSSFSheet worksheet = workbook.createSheet(sheet);
      int i = 0;
      for (Entry<String, Integer> entry : map.entrySet()) {
        // Create ROW-1
        HSSFRow row1 = worksheet.createRow((short) i);
        // Create COL-A from ROW-1 and set data
        HSSFCell cellA1 = row1.createCell((short) 0);
        cellA1.setCellValue(entry.getKey());
        HSSFCellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.GOLD.index);
        // cellStyle.setFillPattern(HSSFCellStyle);
        //cellA1.setCellStyle(cellStyle);
        // Create COL-B from row-1 and set data
        HSSFCell cellB1 = row1.createCell((short) 1);
        cellB1.setCellValue(entry.getValue() + "");
        cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(HSSFColor.LIGHT_CORNFLOWER_BLUE.index);
        //cellB1.setCellStyle(cellStyle);
        i++;
      }
      // Save the workbook in .xls file
      workbook.write(fos);
      fos.flush();
      fos.close();
      workbook.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
