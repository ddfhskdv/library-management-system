package com.college.library.dto;

/**
 * DTO for toggling member active status.
 */
import jakarta.validation.constraints.NotNull;

public class MemberStatusRequest {

    @NotNull
    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
