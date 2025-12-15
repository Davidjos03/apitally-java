package io.apitally.spring.app;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.apitally.spring.ApitallyConsumer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@Validated
public class TestController {
    @GetMapping("/items")
    public List<TestItem> getItems(HttpServletRequest request,
            @RequestParam(required = false) @Size(min = 2, max = 10) String name) {
        ApitallyConsumer consumer = new ApitallyConsumer("tester", "Tester", "Test Group");
        request.setAttribute("apitallyConsumer", consumer);
        List<TestItem> items = new ArrayList<TestItem>();
        items.add(new TestItem(1, "bob"));
        items.add(new TestItem(2, "alice"));
        return items;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public void addItem(@Valid @RequestBody TestItem newItem) {
    }

    @GetMapping("/items/{id}")
    public TestItem getItem(@PathVariable @Min(1) Integer id) {
        TestItem item = new TestItem(id, "bob");
        return item;
    }

    @PutMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateItem(@Valid @RequestBody TestItem newItem, @PathVariable @Min(1) Integer id) {
    }

    @DeleteMapping(value = "/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable @Min(1) Integer id) {
    }

    @GetMapping(value = "/throw", produces = "application/json; charset=utf-8")
    public String getError() {
        throw new TestException("test");
    }

    @ExceptionHandler({ ConstraintViolationException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException e) {
        return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage())).build();
    }
}
