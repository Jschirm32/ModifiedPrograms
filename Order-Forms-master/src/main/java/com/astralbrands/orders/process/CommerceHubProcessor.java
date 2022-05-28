package com.astralbrands.orders.process;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.StringJoiner;

import org.apache.camel.Exchange;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
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
	This Processor takes the input file and formats
	a text file containing active orders. It formats the data
	in an 'IFILE' format for X3. It stores the same data to later be
	processed into a '.csv' file

 */
@Component
public class CommerceHubProcessor implements BrandOrderForms, AppConstants {
	
	Logger log = LoggerFactory.getLogger(CommerceHubProcessor.class);

	@Autowired
	X3BPCustomerDao x3BPCustomerDao;
	
	@Override
	public void process(Exchange exchange, String site, String[] fileNameData) {
		try {
			InputStream inputStream = exchange.getIn().getBody(InputStream.class);
			Workbook workbook = new XSSFWorkbook(inputStream);
			StringBuilder txtFileBuilder = new StringBuilder();
			Sheet firstSheet = workbook.getSheetAt(0);
			String txtFileData = populateTxtString(firstSheet,txtFileBuilder);
			String today = currentDate();
			System.out.println("Output data is : "+ txtFileData);

			if (txtFileData != null) {
				exchange.setProperty(INPUT_FILE_NAME, "CommerceHub.txt");
				exchange.getMessage().setBody(txtFileData);
				exchange.getMessage().setHeader(Exchange.FILE_NAME,  today + "_CommerceHub" + DOT_TXT);
				exchange.setProperty(IS_DATA_PRESENT, true);
			} else {
				exchange.setProperty(IS_DATA_PRESENT, false);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	/*
		Iterates through each row in the current sheet and adds
		each cell's value to a StringBuilder with a delimiter of '~'
		for the IFile format ||| Calls two functions within this class
		to populate the header, and iterate/retrieve every cell value
		Returns a formatted string with data for every cell in the sheet
	 */
	private String populateTxtString(Sheet firstSheet, StringBuilder txtFileBuilder) {

		boolean skipHeader = true;
	
		String tmpPO = EMPTY_STR;
		System.out.println("String builder is : "+ txtFileBuilder.toString());
		System.out.println("Number of cells are : "+firstSheet);
		
		for(Row row : firstSheet) {
			ArrayList<Cell> cells = new ArrayList<>();
			Iterator<Cell> cellIterator = row.cellIterator();
			cellIterator.forEachRemaining(cells::add);
			//cells.size();
			System.out.println(cells.size());
			if(cells.size()>3) {
				if(skipHeader) {
					skipHeader=false;
				}
				else if(tmpPO.equals(getValue(cells.get(3)))){
					txtFileBuilder.append(getOrderLine(row));
					//System.out.println("String builder is : "+ txtFileBuilder.toString());
					txtFileBuilder.append(NEW_LINE_STR);
					tmpPO=getValue(cells.get(3));
					System.out.println("tmpPo number is : "+ tmpPO);
				}
				else {
					System.out.println("row value is "+row.getCell(2));
					txtFileBuilder.append(getHeader(row));
					//System.out.println("String builder is : "+ txtFileBuilder.toString());
					txtFileBuilder.append(NEW_LINE_STR);
					txtFileBuilder.append(getOrderLine(row));
					System.out.println("String builder is : "+ txtFileBuilder.toString());
					txtFileBuilder.append(NEW_LINE_STR);
					tmpPO=getValue(cells.get(3));
					System.out.println("tmpPo number is : "+ tmpPO);
				}
			}
		}
		System.out.println("Text file is : "+txtFileBuilder.toString());
		return txtFileBuilder.toString();
	}

	/*
		Function to format a row in the current sheet being processed
		Iterates through each cell and adds the cell's value in a specific order
		through a set of calls to the "add" method - Every value separated with '~'
	 */
	private String getOrderLine(Row row) {
		
		ArrayList<Cell> cells = new ArrayList<>();
		Iterator<Cell> cellIterator = row.cellIterator();
		cellIterator.forEachRemaining(cells::add);
//		System.out.println("row is : "+cellIterator);
 		StringJoiner lineBuilder = new StringJoiner("~");
		System.out.println("Line builder is : "+ lineBuilder.toString() + "\n");
		lineBuilder.add("L");
		lineBuilder.add(getValue(cells.get(21))); //SKU
		lineBuilder.add(getValue(cells.get(22))); //Description
		lineBuilder.add("BUTCO"); //site
		lineBuilder.add("EA"); //Sales Unit
		lineBuilder.add(getValue(cells.get(23))); //Quantity
		lineBuilder.add("0" + getValue(cells.get(24)).replace(".", "") + "0"); //Gross price
		lineBuilder.add(ZERO);
		lineBuilder.add(EMPTY_STR);
		return lineBuilder.toString();
	}

	
	public String getData(Cell cell) {
		return new DataFormatter().formatCellValue(cell);
	}

	private String getValue(Cell cell) {
		System.out.println("Value is : "+cell.toString());
		String value = getData(cell);
//		System.out.println("Value is : "+value);
		if(value.toString().equalsIgnoreCase("N/A")) {
			return EMPTY_STR;
		}
		return value.toString();
	}

	private String getHeader(Row row) {
		
		ArrayList<Cell> cells = new ArrayList<>();
		Iterator<Cell> cellIterator = row.cellIterator();
		cellIterator.forEachRemaining(cells::add);
		
		StringJoiner headerBuilder = new StringJoiner("~");
		System.out.println("headerBuilder is : "+ headerBuilder.toString());
		//System.out.println(Header is : "+row);
		headerBuilder.add("E");
		headerBuilder.add("BUTCO"); //Sales Site/SALFCY
		headerBuilder.add("BQVCD"); //Order Type/SOHTYP
		headerBuilder.add(getValue(cells.get(3))); //PO number
		headerBuilder.add("460000147"); //BPCORD
		headerBuilder.add(getDate(getValue(cells.get(1)))); //Date
		headerBuilder.add(getValue(cells.get(4))); //Customer order reference
		headerBuilder.add("BUTCO"); // Shipping site
		headerBuilder.add("USD"); //Currency
		for(int i=0; i<5; i++) {
			headerBuilder.add(EMPTY_STR);
		}
		headerBuilder.add(getValue(cells.get(5))); //Bill firstName 
		headerBuilder.add(EMPTY_STR); //Bill lastName 
		headerBuilder.add(getValue(cells.get(11))); //Bill country
		headerBuilder.add(getValue(cells.get(6))); //Bill Add 1
		headerBuilder.add(getValue(cells.get(7))); //Bill Add 2
		headerBuilder.add(getValue(cells.get(10))); //Bill postal code
		headerBuilder.add(getValue(cells.get(8))); // Bill city
		headerBuilder.add(getValue(cells.get(9))); //Bill state
		headerBuilder.add(getValue(cells.get(12))); //Ship firstname
		headerBuilder.add(EMPTY_STR); //Ship LastName
		headerBuilder.add(getValue(cells.get(18))); //Ship country
		headerBuilder.add(getValue(cells.get(13))); //Ship Add 1
		headerBuilder.add(getValue(cells.get(14))); //Ship Add 2
		headerBuilder.add(getValue(cells.get(17))); //Ship Postal code
		headerBuilder.add(getValue(cells.get(15))); //Ship city
		headerBuilder.add(getValue(cells.get(16))); //Ship State
		headerBuilder.add(ZERO);
		headerBuilder.add(ZERO);
		headerBuilder.add(getValue(cells.get(19))); // Tax
		headerBuilder.add(EMPTY_STR);
		headerBuilder.add(EMPTY_STR);
		headerBuilder.add("NET90");
		return headerBuilder.toString();
	}

	private String getDate(String date) {
		System.out.println("Date is : "+date);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yy");
	    LocalDate ld = LocalDate.parse(date,formatter);
		//System.out.println("Date is : "+ld.getMonthValue()+" "+ld.getDayOfMonth()+" "+ld.getYear());
		return ld.getYear()+""+(ld.getMonthValue()<10?("0"+ld.getMonthValue()):ld.getMonthValue())+""+(ld.getDayOfMonth()<10?("0"+ld.getDayOfMonth()):ld.getDayOfMonth());
	}
	private String currentDate() {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		LocalDate date = LocalDate.now();
//		LocalDate date = LocalDate.parse(today, formatter);
		String td = date.format(formatter);
		return td;
	}

}
