package com.golf.tournament.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateResponse {
    private int actualizados;
    private int creados;
    @Builder.Default
    private List<String> matriculasNoProcesadas = new ArrayList<>();
}
