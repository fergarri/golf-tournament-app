import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import './HeaderActionDropdown.css';

export interface HeaderActionItem {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  variant?: 'default' | 'primary' | 'secondary' | 'danger' | 'success' | 'warning';
}

interface HeaderActionDropdownProps {
  label: string;
  items: HeaderActionItem[];
  /** Clase del botón disparador; por defecto mismo tamaño que btn-back */
  triggerClassName?: string;
}

const variantClasses: Record<string, string> = {
  default: 'text-slate-700 hover:bg-slate-50',
  primary: 'text-sky-700 hover:bg-sky-50',
  secondary: 'text-violet-700 hover:bg-violet-50',
  danger: 'text-red-600 hover:bg-red-50',
  success: 'text-emerald-700 hover:bg-emerald-50',
  warning: 'text-amber-700 hover:bg-amber-50',
};

const HeaderActionDropdown = ({
  label,
  items,
  triggerClassName = 'btn-header-dropdown',
}: HeaderActionDropdownProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const visibleItems = items.filter(Boolean);

  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node) &&
        triggerRef.current &&
        !triggerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
        setPosition(null);
      }
    };

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
        setPosition(null);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [isOpen]);

  if (visibleItems.length === 0) return null;

  const handleToggle = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    if (isOpen) {
      setIsOpen(false);
      setPosition(null);
      return;
    }
    if (triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect();
      const menuWidth = 220;
      const left = Math.min(rect.left, window.innerWidth - menuWidth - 8);
      setPosition({
        top: rect.bottom + 4,
        left: Math.max(8, left),
      });
    }
    setIsOpen(true);
  };

  const handleItemClick = (item: HeaderActionItem) => {
    if (item.disabled) return;
    item.onClick();
    setIsOpen(false);
    setPosition(null);
  };

  const dropdownContent = isOpen && position && (
    <div
      ref={menuRef}
      className="header-action-dropdown-menu"
      style={{ top: `${position.top}px`, left: `${position.left}px` }}
      role="menu"
    >
      {visibleItems.map((item, index) => (
        <button
          key={`${item.label}-${index}`}
          type="button"
          role="menuitem"
          disabled={item.disabled}
          className={cn(
            'header-action-dropdown-item',
            variantClasses[item.variant || 'default'],
            item.disabled && 'opacity-50 cursor-not-allowed hover:bg-transparent'
          )}
          onClick={(e) => {
            e.stopPropagation();
            handleItemClick(item);
          }}
        >
          {item.label}
        </button>
      ))}
    </div>
  );

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={triggerClassName}
        onClick={handleToggle}
        aria-haspopup="menu"
        aria-expanded={isOpen}
      >
        {label}
        <ChevronDown className={cn('h-4 w-4 transition-transform', isOpen && 'rotate-180')} />
      </button>
      {dropdownContent && createPortal(dropdownContent, document.body)}
    </>
  );
};

export default HeaderActionDropdown;
