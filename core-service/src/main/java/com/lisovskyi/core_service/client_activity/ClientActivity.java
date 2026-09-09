package com.lisovskyi.core_service.client_activity;

import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.jpa.autoconfigure.entity.CreationTimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "client_activities")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class ClientActivity extends CreationTimestampedEntity {

    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, referencedColumnName = "id")
    private Client client;

    @Column(name = "activity", nullable = false)
    private String activity;
}
