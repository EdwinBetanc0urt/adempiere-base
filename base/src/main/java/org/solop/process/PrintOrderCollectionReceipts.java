/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2006-2017 ADempiere Foundation, All Rights Reserved.         *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * or (at your option) any later version.                                     *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * or via info@adempiere.net                                                  *
 * or https://github.com/adempiere/adempiere/blob/develop/license.html        *
 *****************************************************************************/

package org.solop.process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pdf.IText7Document;
import org.compiere.model.MTable;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfo;
import org.eevolution.services.dsl.ProcessBuilder;

/**
 * Print Collection Receipts for a whole Order.
 * <p>
 * The record of this process is a {@code C_Order}. It evaluates the voucher
 * records stored in {@code C_PaymentProcessorLog} (each merchant copy and each
 * client copy is a separate row) for the payments of that order, and runs the
 * per-record report {@link PrintCollectionReceipts} once for every voucher,
 * producing a single-page PDF each. All the PDFs are then merged into one and
 * exposed as the PDF report of this process, so the caller receives one
 * combined document (same idea as the POS PrintTicket flow).
 *
 * @author Solop
 */
public class PrintOrderCollectionReceipts extends PrintOrderCollectionReceiptsAbstract {

	@Override
	protected void prepare() {
		super.prepare();
	}

	@Override
	protected String doIt() throws Exception {
		final int orderId = getRecord_ID();
		if (orderId <= 0) {
			throw new AdempiereException("@C_Order_ID@ @NotFound@");
		}

		final int collectionReceiptProcessId = PrintCollectionReceipts.getProcessId();

		// One voucher log record = one receipt (merchant copy and client copy are separate rows).
		// The per-record report is run once per record (each a single-page PDF), then merged.
		final String whereClause = "C_PaymentProcessorLog.Value='Voucher' " +
			"AND C_PaymentProcessorLog.C_Payment_ID IN (SELECT p.C_Payment_ID FROM C_Payment AS p WHERE p.C_Order_ID = ?)";
		List<Integer> paymentProcessorLogIds = new Query(
			getCtx(),
			"C_PaymentProcessorLog",
			whereClause,
			get_TrxName()
		)
			.setParameters(orderId)
			.setOrderBy("C_PaymentProcessorLog.C_PaymentProcessorLog_ID")
			.getIDsAsList();
		if (paymentProcessorLogIds.isEmpty()) {
			return "@NoVoucher@";
		}

		final int paymentProcessorLogTableId = MTable.get(getCtx(), "C_PaymentProcessorLog").getAD_Table_ID();

		List<File> pdfList = new ArrayList<>();
		paymentProcessorLogIds.forEach(paymentProcessorLogId -> {
			ProcessBuilder builder = ProcessBuilder.create(getCtx())
				.process(collectionReceiptProcessId)
				.withRecordId(paymentProcessorLogTableId, paymentProcessorLogId)
				.withoutPrintPreview()
				.withoutBatchMode()
				.withWindowNo(0)
				.withoutTransactionClose()
				.withReportExportFormat("pdf")
			;
			ProcessInfo info = builder.execute(get_TrxName());
			File collectFile = info.getPDFReport();
			if (collectFile != null) {
				pdfList.add(collectFile);
			}
		});

		if (pdfList.isEmpty()) {
			return "@NoVoucher@";
		}

		try {
			File resultPdf = File.createTempFile("CollectionReceipts", ".pdf");
			IText7Document.mergePdf(pdfList, resultPdf);
			getProcessInfo().setPDFReport(resultPdf);
		} catch (Exception e) {
			throw new AdempiereException(e.getLocalizedMessage(), e);
		}

		return "@OK@";
	}
}
