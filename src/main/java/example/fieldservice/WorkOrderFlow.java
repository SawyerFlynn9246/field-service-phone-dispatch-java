package example.fieldservice;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderFlow {
    private final InfraiGateway gateway;
    private final Map<String, WorkOrder> pending = new ConcurrentHashMap<>();

    public WorkOrderFlow(InfraiGateway gateway) { this.gateway = gateway; }

    public record WorkOrder(String orderId, String phone, List<String> photoIds,
                            String dispatchStatus, String technicianFollowUp) {}
    public record Start(String orderId, String phone, List<String> photoIds, String requestId) {}
    public record Confirm(String orderId, String phone, String code, String requestId) {}
    public record DispatchResult(WorkOrder workOrder, JsonNode identity) {}

    public WorkOrder start(Start input) {
        if (input.orderId() == null || input.orderId().isBlank() || input.phone() == null || input.phone().isBlank()
            || input.requestId() == null || input.requestId().isBlank() || input.photoIds() == null) {
            throw new IllegalArgumentException("orderId, phone, photoIds and requestId are required");
        }
        WorkOrder order = new WorkOrder(input.orderId(), input.phone(), List.copyOf(input.photoIds()),
                                        "AWAITING_PHONE", "PENDING");
        gateway.sendOtp(input.phone(), input.requestId());
        pending.putIfAbsent(input.orderId(), order);
        return pending.get(input.orderId());
    }

    public DispatchResult confirm(Confirm input) {
        WorkOrder order = pending.get(input.orderId());
        if (order == null || !order.phone().equals(input.phone())) {
            throw new IllegalArgumentException("No matching pending work order");
        }
        if (input.code() == null || input.code().isBlank() || input.requestId() == null || input.requestId().isBlank()) {
            throw new IllegalArgumentException("code and requestId are required");
        }
        JsonNode identity = gateway.verifyPhone(input.phone(), input.code(), input.requestId());
        WorkOrder dispatched = new WorkOrder(order.orderId(), order.phone(), order.photoIds(),
                                             "DISPATCHED", "CALL_CUSTOMER");
        pending.replace(order.orderId(), order, dispatched);
        return new DispatchResult(pending.get(order.orderId()), identity);
    }
}
