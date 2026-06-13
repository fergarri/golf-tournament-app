/**
 * Calcula el ranking estándar de competición (estilo 1-2-2-4).
 * Jugadores con los mismos puntos comparten posición; el siguiente
 * toma el número que corresponde si todos los empatados ocuparan
 * posiciones consecutivas.
 *
 * @param sortedRows Array ya ordenado de mayor a menor puntuación.
 * @param index      Índice del elemento cuya posición se quiere calcular.
 * @param getPoints  Función que extrae el valor numérico de puntos/score del elemento.
 */
export function standardRank<T>(
  sortedRows: T[],
  index: number,
  getPoints: (row: T) => number,
): number {
  const pts = getPoints(sortedRows[index]);
  return sortedRows.findIndex((r) => getPoints(r) === pts) + 1;
}

/**
 * Calcula los rowspan para agrupar visualmente filas con la misma posición.
 * Retorna un array donde:
 *   - valor > 0: esta fila es la primera del grupo; renderizar <td rowSpan={valor}>
 *   - valor = 0: esta fila está cubierta por el rowspan anterior; NO renderizar el <td>
 *
 * @param sortedRows Array ya ordenado.
 * @param getPoints  Función que extrae el score del elemento (mismo que en standardRank).
 */
export function computeRowspans<T>(
  sortedRows: T[],
  getPoints: (row: T) => number,
): number[] {
  const spans = new Array<number>(sortedRows.length).fill(0);
  let i = 0;
  while (i < sortedRows.length) {
    const score = getPoints(sortedRows[i]);
    let count = 1;
    while (i + count < sortedRows.length && getPoints(sortedRows[i + count]) === score) {
      count++;
    }
    spans[i] = count;
    i += count;
  }
  return spans;
}
