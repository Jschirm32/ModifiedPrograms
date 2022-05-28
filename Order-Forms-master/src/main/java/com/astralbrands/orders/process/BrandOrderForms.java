package com.astralbrands.orders.process;

import org.apache.camel.Exchange;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;

/*
	Interface used by the other processor classes
	to retrieve and format a cell's value within an Excel sheet
 */
public interface BrandOrderForms {

	public void process(Exchange exchange, String site, String[] fileName);

	default String getData(Cell cell) {
		return new DataFormatter().formatCellValue(cell);
	}
}
