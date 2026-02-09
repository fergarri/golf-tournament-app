import { useState, useMemo } from 'react';
import './Table.css';

interface Column<T> {
  header: string;
  accessor: keyof T | ((row: T) => React.ReactNode);
  width?: string;
}

interface TableProps<T> {
  data: T[];
  columns: Column<T>[];
  onEdit?: (row: T) => void;
  onDelete?: (row: T) => void;
  customActions?: (row: T) => React.ReactNode;
  emptyMessage?: string;
  getRowKey?: (row: T, index: number) => string | number;
}

type SortDirection = 'asc' | 'desc' | null;

function Table<T>({
  data,
  columns,
  onEdit,
  onDelete,
  customActions,
  emptyMessage = 'No data available',
  getRowKey,
}: TableProps<T>) {
  const [sortColumn, setSortColumn] = useState<number | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>(null);

  const getKey = (row: T, index: number): string | number => {
    if (getRowKey) return getRowKey(row, index);
    const rowAsAny = row as any;
    if (rowAsAny.id !== undefined) return rowAsAny.id;
    return index;
  };

  const renderCell = (row: T, column: Column<T>) => {
    if (typeof column.accessor === 'function') {
      return column.accessor(row);
    }
    return String(row[column.accessor] ?? '-');
  };

  const getCellValue = (row: T, column: Column<T>): any => {
    if (typeof column.accessor === 'function') {
      const result = column.accessor(row);
      // Si es un ReactNode, intentar extraer el texto
      if (typeof result === 'string' || typeof result === 'number') {
        return result;
      }
      return String(result);
    }
    return row[column.accessor];
  };

  const sortedData = useMemo(() => {
    if (sortColumn === null || sortDirection === null) {
      return data;
    }

    const column = columns[sortColumn];
    return [...data].sort((a, b) => {
      const aValue = getCellValue(a, column);
      const bValue = getCellValue(b, column);

      // Manejar valores nulos o undefined
      if (aValue === null || aValue === undefined || aValue === '-') return 1;
      if (bValue === null || bValue === undefined || bValue === '-') return -1;

      // Comparación numérica
      if (typeof aValue === 'number' && typeof bValue === 'number') {
        return sortDirection === 'asc' ? aValue - bValue : bValue - aValue;
      }

      // Comparación de strings (case insensitive)
      const aStr = String(aValue).toLowerCase();
      const bStr = String(bValue).toLowerCase();

      if (aStr < bStr) return sortDirection === 'asc' ? -1 : 1;
      if (aStr > bStr) return sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }, [data, sortColumn, sortDirection, columns]);

  const handleSort = (columnIndex: number) => {
    if (sortColumn === columnIndex) {
      // Ciclar entre: asc -> desc -> null
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else if (sortDirection === 'desc') {
        setSortDirection(null);
        setSortColumn(null);
      }
    } else {
      setSortColumn(columnIndex);
      setSortDirection('asc');
    }
  };

  return (
    <div className="table-container">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((column, index) => (
              <th 
                key={index} 
                style={{ width: column.width }}
                className="sortable-header"
                onClick={() => handleSort(index)}
              >
                <div className="header-content">
                  <span>{column.header}</span>
                  <span className="sort-icon">
                    {sortColumn === index ? (
                      sortDirection === 'asc' ? '▲' : '▼'
                    ) : (
                      '⇅'
                    )}
                  </span>
                </div>
              </th>
            ))}
            {(onEdit || onDelete || customActions) && (
              <th style={{ width: '180px' }} className="non-sortable-header">
                Acciones
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {sortedData.length === 0 ? (
            <tr>
              <td colSpan={columns.length + (onEdit || onDelete || customActions ? 1 : 0)} className="empty-row">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            sortedData.map((row, index) => (
              <tr key={getKey(row, index)}>
                {columns.map((column, colIndex) => (
                  <td key={colIndex}>{renderCell(row, column)}</td>
                ))}
                {(onEdit || onDelete || customActions) && (
                  <td>
                    <div className="action-buttons">
                      {customActions ? (
                        customActions(row)
                      ) : (
                        <>
                          {onEdit && (
                            <button onClick={() => onEdit(row)} className="btn-edit">
                              Editar
                            </button>
                          )}
                          {onDelete && (
                            <button onClick={() => onDelete(row)} className="btn-delete">
                              Eliminar
                            </button>
                          )}
                        </>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default Table;
