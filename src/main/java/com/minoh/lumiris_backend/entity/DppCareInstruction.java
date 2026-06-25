package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "dpp_care_instructions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"dpp_form_id", "care_code"})
)
@Getter
@Setter
@NoArgsConstructor
public class DppCareInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id", nullable = false)
    private DppForm dppForm;

    @Column(name = "care_code", nullable = false)
    private String careCode;
}
