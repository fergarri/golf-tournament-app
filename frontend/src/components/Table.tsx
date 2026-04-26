import { useState, useMemo } from 'react';
import ActionMenu, { ActionMenuItem } from './ActionMenu';
import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';

interface Column<T> {
  header: string;
  accessor: keyof T | ((row: T) => React.ReactNode);
  /** Valor usado para ordenar cuando el accessor devuelve JSX */
  sortValue?: (row: T) => string | number;
  width?: string;
}

export interface TableAction<T> {
  label: string;
  onClick: (row: T) => void;
  variant?: 'default' | 'primary' | 'secondary' | 'danger';
  icon?: string;
  show?: (row: T) => boolean;
}

interface TableProps<T> {
  data: T[];
  columns: Column<T>[];
  onEdit?: (row: T) => void;
  onDelete?: (row: T) => void;
  customActions?: (row: T) => React.ReactNode;
  actions?: TableAction<T>[];
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
  actions,
  emptyMessage = 'No hay datos disponibles',
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
    if (column.sortValue) return column.sortValue(row);
    if (typeof column.accessor === 'function') {
      const result = column.accessor(row);
      if (typeof result === 'string' || typeof result === 'number') return result;
      return String(result);
    }
    return row[column.accessor];
  };

  const sortedData = useMemo(() => {
    if (sortColumn === null || sortDirection === null) return data;
    const column = columns[sortColumn];
    return [...data].sort((a, b) => {
      const aValue = getCellValue(a, column);
      const bValue = getCellValue(b, column);
      if (aValue === null || aValue === undefined || aValue === '-') return 1;
      if (bValue === null || bValue === undefined || bValue === '-') return -1;
      if (typeof aValue === 'number' && typeof bValue === 'number') {
        return sortDirection === 'asc' ? aValue - bValue : bValue - aValue;
      }
      const aStr = String(aValue).toLowerCase();
      const bStr = String(bValue).toLowerCase();
      if (aStr < bStr) return sortDirection === 'asc' ? -1 : 1;
      if (aStr > bStr) return sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }, [data, sortColumn, sortDirection, columns]);

  const handleSort = (columnIndex: number) => {
    if (sortColumn === columnIndex) {
      if (sortDirection === 'asc') setSortDirection('desc');
      else if (sortDirection === 'desc') { setSortDirection(null); setSortColumn(null); }
    } else {
      setSortColumn(columnIndex);
      setSortDirection('asc');
    }
  };

  const buildMenuItems = (row: T): ActionMenuItem[] => {
    const menuItems: ActionMenuItem[] = [];
    if (actions) {
      actions.forEach(action => {
        if (!action.show || action.show(row)) {
          menuItems.push({ label: action.label, onClick: () => action.onClick(row), variant: action.variant, icon: action.icon });
        }
      });
    } else {
      if (onEdit) menuItems.push({ label: 'Editar', onClick: () => onEdit(row), variant: 'primary' });
      if (onDelete) menuItems.push({ label: 'Eliminar', onClick: () => onDelete(row), variant: 'danger' });
    }
    return menuItems;
  };

  const hasActions = !!(onEdit || onDelete || customActions || actions);

  return (
    <div className="rounded-lg border border-border bg-white shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-slate-50">
              {columns.map((column, index) => (
                <th
                  key={index}
                  style={{ width: column.width }}
                  className="px-4 py-3 text-left font-semibold text-slate-600 cursor-pointer select-none hover:bg-slate-100 transition-colors"
                  onClick={() => handleSort(index)}
                >
                  <div className="flex items-center gap-1.5">
                    <span>{column.header}</span>
                    <span className="text-slate-400">
                      {sortColumn === index ? (
                        sortDirection === 'asc' ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />
                      ) : (
                        <ChevronsUpDown className="h-3.5 w-3.5" />
                      )}
                    </span>
                  </div>
                </th>
              ))}
              {hasActions && (
                <th className="px-4 py-3 text-left font-semibold text-slate-600 w-[60px]">
                  Acciones
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {sortedData.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length + (hasActions ? 1 : 0)}
                  className="px-4 py-10 text-center text-slate-400 italic"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              sortedData.map((row, index) => (
                <tr
                  key={getKey(row, index)}
                  className="border-b border-border last:border-0 hover:bg-slate-50 transition-colors"
                >
                  {columns.map((column, colIndex) => (
                    <td key={colIndex} className="px-4 py-3 text-slate-700">
                      {renderCell(row, column)}
                    </td>
                  ))}
                  {hasActions && (
                    <td className="px-4 py-3">
                      {customActions ? customActions(row) : <ActionMenu items={buildMenuItems(row)} />}
                    </td>
                  )}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default Table;
