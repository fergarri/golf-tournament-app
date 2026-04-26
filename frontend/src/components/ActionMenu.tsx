import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { MoreVertical } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface ActionMenuItem {
  label: string;
  onClick: () => void;
  variant?: 'default' | 'primary' | 'secondary' | 'danger';
  icon?: string;
}

interface ActionMenuProps {
  items: ActionMenuItem[];
}

const variantClasses: Record<string, string> = {
  default: 'text-slate-700 hover:bg-slate-50',
  primary: 'text-emerald-700 hover:bg-emerald-50',
  secondary: 'text-violet-700 hover:bg-violet-50',
  danger: 'text-red-600 hover:bg-red-50',
};

const ActionMenu = ({ items }: ActionMenuProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState<{ top: number; left: number; openUpward: boolean } | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node) &&
        triggerRef.current &&
        !triggerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      if (triggerRef.current) {
        const rect = triggerRef.current.getBoundingClientRect();
        const menuHeight = 250;
        const spaceBelow = window.innerHeight - rect.bottom;
        const openUpward = spaceBelow < menuHeight && rect.top > menuHeight;
        setPosition({
          top: openUpward ? rect.top - menuHeight : rect.bottom + 4,
          left: rect.right - 160,
          openUpward,
        });
      }
    } else {
      setPosition(null);
    }

    return () => { document.removeEventListener('mousedown', handleClickOutside); };
  }, [isOpen]);

  const handleItemClick = (item: ActionMenuItem) => {
    item.onClick();
    setIsOpen(false);
  };

  const dropdownContent = isOpen && position && (
    <div
      ref={menuRef}
      className="fixed z-[10000] min-w-[160px] bg-white rounded-lg shadow-lg border border-slate-200 py-1 animate-in fade-in-0 zoom-in-95"
      style={{ top: `${position.top}px`, left: `${position.left}px` }}
    >
      {items.map((item, index) => (
        <button
          key={index}
          className={cn(
            'w-full px-4 py-2.5 text-sm font-medium text-left flex items-center gap-2.5 transition-colors',
            variantClasses[item.variant || 'default']
          )}
          onClick={(e) => { e.stopPropagation(); handleItemClick(item); }}
        >
          {item.icon && <span className="text-base">{item.icon}</span>}
          <span>{item.label}</span>
        </button>
      ))}
    </div>
  );

  return (
    <>
      <button
        ref={triggerRef}
        className="w-8 h-8 flex items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-700 transition-colors"
        onClick={(e) => { e.stopPropagation(); setIsOpen(!isOpen); }}
        aria-label="Más acciones"
      >
        <MoreVertical className="h-4 w-4" />
      </button>
      {dropdownContent && createPortal(dropdownContent, document.body)}
    </>
  );
};

export default ActionMenu;
