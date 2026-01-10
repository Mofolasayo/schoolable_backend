package com.schoolable.backend.task;

import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class TaskSpecifications {

    private TaskSpecifications() {}

    public static Specification<Task> hasAssignee(UUID assigneeId) {
        return (root, query, cb) -> assigneeId == null ? cb.conjunction() : cb.equal(root.get("assigneeId"), assigneeId);
    }

    public static Specification<Task> hasDepartment(String department) {
        return (root, query, cb) -> department == null ? cb.conjunction() : cb.equal(root.get("organization"), department);
    }

    public static Specification<Task> hasStatus(String status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(String priority) {
        return (root, query, cb) -> priority == null ? cb.conjunction() : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> titleContains(String queryText) {
        return (root, query, cb) -> queryText == null ? cb.conjunction() : cb.like(cb.lower(root.get("title")), "%" + queryText.toLowerCase() + "%");
    }
}
