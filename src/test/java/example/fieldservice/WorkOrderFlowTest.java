package example.fieldservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class WorkOrderFlowTest {
    @Test
    void dispatchRequiresMatchingPhoneAndSuccessfulVerification() {
        InfraiGateway gateway = mock(InfraiGateway.class);
        WorkOrderFlow flow = new WorkOrderFlow(gateway);
        flow.start(new WorkOrderFlow.Start("WO-17", "+15550100000", List.of("photo-1"), "start-17"));
        assertThrows(IllegalArgumentException.class, () ->
            flow.confirm(new WorkOrderFlow.Confirm("WO-17", "+15550999999", "123456", "verify-17")));
        verify(gateway, never()).verifyPhone(anyString(), anyString(), anyString());
        when(gateway.verifyPhone("+15550100000", "123456", "verify-17"))
            .thenReturn(new ObjectMapper().createObjectNode().put("user_id", "tech-1"));
        var result = flow.confirm(new WorkOrderFlow.Confirm("WO-17", "+15550100000", "123456", "verify-17"));
        assertEquals("DISPATCHED", result.workOrder().dispatchStatus());
        assertEquals("CALL_CUSTOMER", result.workOrder().technicianFollowUp());
        assertEquals(List.of("photo-1"), result.workOrder().photoIds());
    }
}
