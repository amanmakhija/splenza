package com.splitwise.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "import_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "SPLITWISE_CSV";

    @Column(name = "file_name")
    private String fileName;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING / SUCCESS / FAILED / PARTIAL

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "imported_rows")
    private Integer importedRows;

    @Column(name = "failed_rows")
    private Integer failedRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_report", columnDefinition = "jsonb")
    private List<Object> errorReport;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
