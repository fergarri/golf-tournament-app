import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import './Pagination.css';

const DEFAULT_PAGE_SIZES = [10, 25, 50];

interface PaginationProps {
  page: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  disabled?: boolean;
  pageSizes?: number[];
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}

const Pagination = ({
  page,
  pageSize,
  totalPages,
  totalElements,
  disabled = false,
  pageSizes = DEFAULT_PAGE_SIZES,
  onPageChange,
  onPageSizeChange,
}: PaginationProps) => {
  const safeTotalPages = Math.max(totalPages, 1);
  const currentPage = totalElements === 0 ? 1 : page + 1;
  const from = totalElements === 0 ? 0 : page * pageSize + 1;
  const to = totalElements === 0 ? 0 : Math.min(page * pageSize + pageSize, totalElements);
  const showFirstPage = totalElements > 0 && page > 0;
  const showLastPage = totalElements > 0 && totalPages > 0 && page < totalPages - 1;

  return (
    <div className="pagination-bar">
      <span className="pagination-summary">
        {totalElements === 0 ? '0 torneos' : `${from}–${to} de ${totalElements}`}
      </span>

      <div className="pagination-nav">
        {showFirstPage && (
          <button
            type="button"
            onClick={() => onPageChange(0)}
            disabled={disabled}
            aria-label="Primera página"
          >
            <ChevronsLeft size={18} />
          </button>
        )}
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={disabled || page <= 0 || totalElements === 0}
          aria-label="Página anterior"
        >
          <ChevronLeft size={18} />
        </button>
        <span className="pagination-status">
          Página {currentPage} de {totalElements === 0 ? 1 : safeTotalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={disabled || page >= totalPages - 1 || totalElements === 0}
          aria-label="Página siguiente"
        >
          <ChevronRight size={18} />
        </button>
        {showLastPage && (
          <button
            type="button"
            onClick={() => onPageChange(totalPages - 1)}
            disabled={disabled}
            aria-label="Última página"
          >
            <ChevronsRight size={18} />
          </button>
        )}
      </div>

      <label className="pagination-size">
        Mostrar
        <select
          value={pageSize}
          disabled={disabled}
          onChange={(event) => onPageSizeChange(Number(event.target.value))}
          aria-label="Cantidad de torneos por página"
        >
          {pageSizes.map((size) => (
            <option key={size} value={size}>
              {size}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
};

export default Pagination;
