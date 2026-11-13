package com.navan.travel.api;

import java.net.URI;

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
import com.navan.travel.transaction.Transaction;
import com.navan.travel.transaction.TransactionService;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@RequestBody CreateTransactionRequest request) {
        Transaction created = transactionService.create(request.toCommand());
        return ResponseEntity.created(location(created.id())).body(TransactionResponse.from(created));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable String id) {
        return TransactionResponse.from(transactionService.get(parseId(id)));
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(@RequestParam(required = false) Integer limit,
                                                  @RequestParam(required = false) String cursor) {
        PageQuery query = PageQuery.of(limit, PageCursor.decode(cursor));
        return PageResponse.from(transactionService.list(query), TransactionResponse::from);
    }

    @PatchMapping("/{id}")
    public TransactionResponse update(@PathVariable String id, @RequestBody PatchTransactionRequest request) {
        return TransactionResponse.from(transactionService.update(parseId(id), request.toChanges()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        transactionService.delete(parseId(id));
    }

    static URI location(long transactionId) {
        return URI.create("/transactions/" + PublicIds.transaction(transactionId));
    }

    static long parseId(String publicId) {
        return PublicIds.parseTransaction(publicId)
                .orElseThrow(() -> new NotFoundException("Transaction " + publicId + " not found"));
    }
}
