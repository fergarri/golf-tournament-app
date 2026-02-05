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

function Table<T>({
  data,
  columns,
  onEdit,
  onDelete,
  customActions,
  emptyMessage = 'No data available',
  getRowKey,
}: TableProps<T>) {
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

  return (
    <div className="table-container">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((column, index) => (
              <th key={index} style={{ width: column.width }}>
                {column.header}
              </th>
            ))}
            {(onEdit || onDelete || customActions) && <th style={{ width: '180px' }}>Acciones</th>}
          </tr>
        </thead>
        <tbody>
          {data.length === 0 ? (
            <tr>
              <td colSpan={columns.length + (onEdit || onDelete || customActions ? 1 : 0)} className="empty-row">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((row, index) => (
              <tr key={getKey(row, index)}>
                {columns.map((column, index) => (
                  <td key={index}>{renderCell(row, column)}</td>
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
