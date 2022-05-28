package com.astralbrands.orders.process;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringJoiner;

import org.apache.camel.Exchange;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.astralbrands.orders.constants.AppConstants;
import com.astralbrands.orders.dao.X3BPCustomerDao;
import org.springframework.stereotype.Component;

/*
	This Processor takes the input file and formats
	a text file containing active orders. It formats the data
	in an 'IFILE' format for X3. It formats a '.csv' file with
	the same data but in an Excel sheet
 */
@Component
public class AloutteOrderProcessor implements BrandOrderForms, AppConstants {
	Logger log = LoggerFactory.getLogger(ReadXslxFileProcessor.class);
	
	@Autowired
	X3BPCustomerDao x3BPCustomerDao;

	@Override
	public void process(Exchange exchange, String site, String[] fileNameData) {
		try {
			InputStream inputStream = exchange.getIn().getBody(InputStream.class);
			Workbook workbook = new XSSFWorkbook(inputStream);
			String headerStr = populateHeader(fileNameData,site);
			StringBuilder prodEntry = new StringBuilder();
			int numOfSheet = workbook.getNumberOfSheets();
			log.info("Number of sheets we are processing : " + numOfSheet);
			for (int i = 0; i < numOfSheet; i++) {
				Sheet firstSheet = workbook.getSheetAt(i);

				readSheet(firstSheet, prodEntry, site);
				log.info("FirstSheet : " + firstSheet);
			}
			String data = headerStr + NEW_LINE_STR + prodEntry.toString();
			if (prodEntry.length() > 0) {
				exchange.getMessage().setBody(data);
				exchange.setProperty(CSV_DATA, data.replace(TILDE, COMMA));
				exchange.setProperty("IFILE", data);
				exchange.getMessage().setHeader(Exchange.FILE_NAME, exchange.getProperty(INPUT_FILE_NAME) + DOT_TXT);
				exchange.setProperty(IS_DATA_PRESENT, true);
				exchange.setProperty(SITE_NAME, site);
			} else {
				exchange.setProperty(IS_DATA_PRESENT, false);
			}
		} catch (Exception e) {
			e.printStackTrace();
			exchange.setProperty(IS_DATA_PRESENT, false);
		}
	}

	private CharSequence getCurrency(String site) {
		if (US_STR.equals(site)) {
			return US_CURR;
		} else {
			return CA_CURR;
		}
	}

	/*
		This method iterates through an entire Excel spreadsheet
		For each row the cell's value for every column is added to
		a StringJoiner - StringBuilder used to separate each row (new line after
		every successfully processed row)
	 */
	private void readSheet(Sheet firstSheet, StringBuilder dataEntry, String site) {
		boolean entryStart = false;
		for (Row row : firstSheet) {
			ArrayList<Cell> cells = new ArrayList<>();
			Iterator<Cell> cellIterator = row.cellIterator();
			cellIterator.forEachRemaining(cells::add);
			StringJoiner entry = new StringJoiner(TILDE);
			if (cells.size() == 0) {
				continue;
			}

			if (cells.size() < 2) {
				entryStart = false;
			}
			if (entryStart && cells.size() > 5) {
				Object qtyValue = getValue(cells.get(4));
				double qty = 0;
				if (qtyValue instanceof Double) {
					qty = (Double) qtyValue;
				}
				if (qty > 0) {
					/*
					 * log.info(cells.size() + " qt " +qty + ", skuid :" +getData(cells.get(2)) +
					 * ", desc :" +getData(cells.get(0)) + ",site :" +
					 * getStockSite(getData(cells.get(cells.size()-1)), site));
					 */
					String cost = getData(cells.get(3));
					String a = cost.substring(1, cost.lastIndexOf("."));
					String b = cost.substring((cost.lastIndexOf(".") + 1));
//					String TotalCost = getData(cells.get(5));
					String skuId = getData(cells.get(2));
					entry.add(CHAR_L);
					entry.add(skuId);
					entry.add(getData(cells.get(0)));
					entry.add(getStockSite(getData(cells.get(cells.size()-1)), site));
					entry.add(EA_STR);
					entry.add(((int) qty) + EMPTY_STR);
					entry.add(a + b);
					// entry.add("");// entry.add(getProdPrice(skuId, getValue(cells.get(3))));
					for (int i = 0; i < 28; i++) {
						entry.add(EMPTY_STR);
					}
					dataEntry.append(entry.toString()).append(NEW_LINE_STR);
				}
			}
			if (cells.size() > 5 && !entryStart) {
				String colName = cells.get(4).getStringCellValue();
				if (QUANTITY.equalsIgnoreCase(colName)) {
					entryStart = true;
				}
			}
		}
	}

	/*
		Function to take a cell (Excel spreadsheet cell) as a param
		Use 'switch' statement to determine the cell value's type
		Retrieves that value and returns it as a type Object
	 */
	private Object getValue(Cell cell) {
		Object value = null;
		FormulaEvaluator evaluator = null;
		switch (cell.getCellType()) {
			case Cell.CELL_TYPE_STRING:
				value = cell.getStringCellValue();
				break;
			case Cell.CELL_TYPE_NUMERIC:
				value = cell.getNumericCellValue();
				break;
//			case Cell.CELL_TYPE_FORMULA:
//				CellValue cellValue = evaluator.evaluate(cell);
//				if (cellValue != null) {
//					double val = cellValue.getNumberValue();
//					value = Math.round(val * 100.0) / 100.0;
//				}
//				break;
		default:
			break;
		}
		return value;
	}

	private String getStockSite(String flag, String site) {
		if (US_STR.equals(site)) {
			return "ALOUS";
		} else {
			if (flag != null && flag.trim().length() > 0 && flag.toLowerCase().equals("yes")) {
				return "ALCCA";
			}
			return "ALCUS";
		}
	}

	private String getSite(String site) {
		if (US_STR.equals(site)) {
			return "ALOUS";
		} else {
			return "ALCUS";
		}
	}

	// Populates the first row, header, in the csv/text file
	public String populateHeader(String[] fileNameData, String site) {
		StringJoiner header = new StringJoiner(TILDE);
		header.add(CHAR_E);
		header.add(getSite(site));
		// header.add("ALOUS");
		header.add(getOrderType(site));
		header.add(EMPTY_STR);
		header.add(fileNameData[0]);
		//header.add(EMPTY_STR); // extra line
		header.add(fileNameData[1]);
		header.add(fileNameData[1]);
		header.add(getSite(site));
		header.add(getCurrency(site));
		for (int i = 0; i < 26; i++) {
				header.add(EMPTY_STR);
		}
		header.add(x3BPCustomerDao.getPaymentTerms(fileNameData[0]));
		//header.add("NET30");
		return header.toString();
	}

	private String getOrderType(String site) {
		if (US_STR.equals(site)) {
			return "AUBLK";
		} else {
			return "ACBLK";
		}
	}

}
