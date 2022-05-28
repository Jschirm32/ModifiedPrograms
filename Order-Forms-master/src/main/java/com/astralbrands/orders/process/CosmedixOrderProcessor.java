package com.astralbrands.orders.process;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.camel.Exchange;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.astralbrands.orders.constants.AppConstants;
import com.astralbrands.orders.dao.X3BPCustomerDao;

/*
	This Processor formats the exchange data and creates
	a text file containing active orders purchased through
	the Cosmedix site formatted in an 'IFILE' format
	for X3
 */
@Component
public class CosmedixOrderProcessor implements BrandOrderForms, AppConstants {
	
	@Autowired
	X3BPCustomerDao x3BPCustomerDao;
	
	public static final String SQID = "SQID";
	public static final String PRODUCTDEC = "PRODUCTDEC";
	public static final String QUANTITY = "QUANTITY";
	public static final String PRICE = "PRICE";
	public static final String Site = "COSMEDIX";
	private String customerRefNumber = "";
	Logger log = LoggerFactory.getLogger(CosmedixOrderProcessor.class);
	static Map<Integer, Map<String, Integer>> colIndexMap = new HashMap<>();
	static {
		Map<String, Integer> firstSheet = new HashMap<>();
		firstSheet.put(SQID, 1);
		firstSheet.put(PRODUCTDEC, 4);
		firstSheet.put(QUANTITY, 11);
		firstSheet.put(PRICE, 10);

		Map<String, Integer> secondSheet = new HashMap<>();
		secondSheet.put(SQID, 0);
		secondSheet.put(PRODUCTDEC, 2);
		secondSheet.put(QUANTITY, 3);
		secondSheet.put(PRICE, 4);

		Map<String, Integer> thirdSheet = new HashMap<>();
		thirdSheet.put(SQID, 0);
		thirdSheet.put(PRODUCTDEC, 2);
		thirdSheet.put(QUANTITY, 6);
		thirdSheet.put(PRICE, 5);

		colIndexMap.put(0, firstSheet);
		colIndexMap.put(1, secondSheet);
		colIndexMap.put(2, thirdSheet);
	}

	@Override
	public void process(Exchange exchange, String site, String[] fileNameData) {
		InputStream inputStream = exchange.getIn().getBody(InputStream.class);
		try {
			Workbook workbook = new XSSFWorkbook(inputStream);
			int numOfSheet = workbook.getNumberOfSheets();

			StringBuilder prodEntry = new StringBuilder();
			log.info("Number of sheets we are processing :" + numOfSheet);
			for (int i = 0; i < numOfSheet; i++) {
				Sheet firstSheet = workbook.getSheetAt(i);
				FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
				readSheet(firstSheet, prodEntry, evaluator, i);
				log.info(i + " sheet name: " + firstSheet);
			}
			String headerStr = populateHeader(fileNameData, site);
			log.info("data entry : " + prodEntry.toString());
			String data = headerStr + NEW_LINE_STR + prodEntry.toString();
			if (data.length() > 0) {
				exchange.getMessage().setBody(data);
				exchange.setProperty(CSV_DATA, data.replace(TILDE, COMMA));
				exchange.setProperty("IFILE", data);
				exchange.getMessage().setHeader(Exchange.FILE_NAME, exchange.getProperty(INPUT_FILE_NAME)+DOT_TXT);
				exchange.setProperty(IS_DATA_PRESENT, true);
				exchange.setProperty(SITE_NAME, Site);
			} else {
				exchange.setProperty(IS_DATA_PRESENT, false);
			}
		} catch (IOException e) {
			exchange.setProperty(IS_DATA_PRESENT, false);
		}
	}

	/*
		Method to process Excel Spreadsheets by their sheetIndex
		'for' loop iterates through every cell for each row and
		adds the cell's value to a StringJoiner with a delimiter of '~'
		Adds each processed row to a StringBuilder for a complete set of
		all cell values
	 */
	private void readSheet(Sheet firstSheet, StringBuilder dataEntry, FormulaEvaluator evaluator, int sheetIndex) {
		boolean entryStart = false;
		Map<String, Integer> indexMap = colIndexMap.get(sheetIndex);
		Optional<Integer> maxValue = indexMap.entrySet().stream().map(entry -> entry.getValue())
				.max(Comparator.comparingInt(Integer::valueOf));
		log.info("sheet index :" + sheetIndex + " and max index :" + maxValue);
		int index = 0;
		StringJoiner entry;
		for (Row row : firstSheet) {
			entry = new StringJoiner(TILDE);
			ArrayList<Cell> cells = new ArrayList<>();
			Iterator<Cell> cellIterator = row.cellIterator();
			cellIterator.forEachRemaining(cells::add);
			Cell firstCol = row.getCell(indexMap.get(SQID));
			String firstHeader = getData(firstCol);
			if (sheetIndex == 0) {
				String refNumValue = getData(row.getCell(1));
				if ("PO #:".equals(refNumValue)) {
					customerRefNumber = getData(row.getCell(2));
				}
			}
			index++;
			if (firstHeader != null && "Item #".equals(firstHeader.trim())) {
				entryStart = true;
				continue;
			}
			if (!entryStart) {
				continue;
			}
			if (entryStart && cells.size() >= maxValue.get()) {
				String sqid = getData(firstCol);
				Cell prodCol = row.getCell(indexMap.get(PRODUCTDEC));
				Cell qtcol = row.getCell(indexMap.get(QUANTITY));
				Cell priceCol = row.getCell(indexMap.get(PRICE));
				log.info(index + " index " + getData(firstCol) + ", prodcol :" + getData(prodCol)
						+ ", qtCol :" + getData(qtcol) + ", price col :" + getData(priceCol));
				String quantity = getData(qtcol);
				if (quantity != null && quantity.trim().length() > 0 && getNumeric(quantity) > 0) {
					entry.add(CHAR_L);
					entry.add(sqid); //ITMREF
					entry.add(getData(prodCol)); //Product Description
					entry.add("COSCO"); //Stock site
					entry.add(EA_STR); //Sales Unit
					entry.add(getData(qtcol));//Quantity 
					entry.add(getValue(priceCol, evaluator));//price
					entry.add(EMPTY_STR);
					entry.add(EMPTY_STR);
					entry.add(EMPTY_STR);
					dataEntry.append(entry).append(NEW_LINE_STR);
				}
			}
		}
	}

	/*
		Function to take a cell (Excel spreadsheet cell) as a param
		Use 'switch' statement to determine the cell value's type
		Retrieves that value and returns it as a type String
		CELL_.._FORMULA - Returns a number value for Price column
		-----Excludes the '$'-------
	 */
	private String getValue(Cell cell, FormulaEvaluator evaluator) {
		Object value = new Object();
		switch (cell.getCellType()) {
		case Cell.CELL_TYPE_STRING:
			value = cell.getStringCellValue();
			break;
		case Cell.CELL_TYPE_NUMERIC:
			value = cell.getNumericCellValue();
			break;
		case Cell.CELL_TYPE_FORMULA:
			CellValue cellValue = evaluator.evaluate(cell);
			if (cellValue != null) {
				double val = cellValue.getNumberValue();
				value = Math.round(val * 100.0) / 100.0;
			}
			break;
		default:
			break;
		}
		return value.toString();
	}

	private int getNumeric(String quantity) {
		try {
			return Integer.parseInt(quantity);
		} catch (Exception e) {
			return 0;
		}
	}

	/*
		Populates the first line in the text/csv file with
		information regarding the orders' origin
	 */
	public String populateHeader(String[] fileNameData, String site) {
		StringJoiner header = new StringJoiner(TILDE);
		header.add(CHAR_E);
		header.add("COSCO");//Sales site
		// header.add("ALOUS");
		header.add("CBLK");//Order type
		header.add(EMPTY_STR);//Order number (blank)
		header.add(fileNameData[0]);
		header.add(fileNameData[1]);
		//header.add(customerRefNumber);
		header.add(fileNameData[1]);
		header.add("COSCO");
		header.add(US_CURR);

		for (int i = 0; i < 26; i++) {
				header.add(EMPTY_STR);
		}
		header.add(x3BPCustomerDao.getPaymentTerms(fileNameData[0]));
		//header.add("NET30");
		return header.toString();
	}

}
