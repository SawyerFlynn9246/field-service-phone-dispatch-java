package example.fieldservice;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DispatchController {
    private final WorkOrderFlow flow;
    public DispatchController(WorkOrderFlow flow) { this.flow = flow; }

    @PostMapping("/orders/phone/start")
    public WorkOrderFlow.WorkOrder start(@RequestBody WorkOrderFlow.Start input) { return flow.start(input); }

    @PostMapping("/orders/phone/confirm")
    public WorkOrderFlow.DispatchResult confirm(@RequestBody WorkOrderFlow.Confirm input) { return flow.confirm(input); }

    @ExceptionHandler(InfraiGateway.Rejection.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiGateway.Rejection error) {
        return ResponseEntity.status(error.status).body(Map.of("code", error.code, "message", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", error.getMessage()));
    }
}
