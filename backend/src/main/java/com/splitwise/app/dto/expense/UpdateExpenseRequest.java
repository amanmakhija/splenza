package com.splitwise.app.dto.expense;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update an existing expense. Uses full-replace semantics - same shape as "
        + "CreateExpenseRequest; every field, including participants, is replaced with what's provided")
// Same shape as create - full replace semantics for Phase 1 simplicity
public class UpdateExpenseRequest extends CreateExpenseRequest {
}
