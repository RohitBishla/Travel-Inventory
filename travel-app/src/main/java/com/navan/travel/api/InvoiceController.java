package com.navan.travel.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.navan.travel.common.NotFoundException;
import com.navan.travel.common.PageQuery;
import com.navan.travel.common.PublicIds;
import com.navan.travel.invoice.Invoice;
import com.navan.travel.invoice.InvoiceService;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@RequestBody InvoiceRequest request) {
        Invoice created = invoiceService.create(transactionIdsOf(request));
        URI location = URI.create("/invoices/" + PublicIds.invoice(created.id()));
        return ResponseEntity.created(location).body(InvoiceResponse.from(created));
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable String id) {
        return InvoiceResponse.from(invoiceService.get(parseId(id)));
    }

    @GetMapping
    public PageResponse<InvoiceSummaryResponse> list(@RequestParam(required = false) Integer limit,
                                                     @RequestParam(required = false) String cursor) {
        PageQuery query = PageQuery.of(limit, PageCursor.decode(cursor));
        return PageResponse.from(invoiceService.list(query), InvoiceSummaryResponse::from);
    }

    @PatchMapping("/{id}")
    public InvoiceResponse replaceTransactions(@PathVariable String id, @RequestBody InvoiceRequest request) {
        long invoiceId = parseId(id);
        return InvoiceResponse.from(invoiceService.replaceTransactions(invoiceId, transactionIdsOf(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        invoiceService.delete(parseId(id));
    }

    private static List<Long> transactionIdsOf(InvoiceRequest request) {
        return request.requestedTransactionIds().stream()
                .map(TransactionController::parseId)
                .toList();
    }

    private static long parseId(String publicId) {
        return PublicIds.parseInvoice(publicId)
                .orElseThrow(() -> new NotFoundException("Invoice " + publicId + " not found"));
    }
}
