import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import './ActionMenu.css';

export interface ActionMenuItem {
  label: string;
  onClick: () => void;
  variant?: 'default' | 'primary' | 'secondary' | 'danger';
  icon?: string;
}

interface ActionMenuProps {
  items: ActionMenuItem[];
}

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
      
      // Calcular posición del menú
      if (triggerRef.current) {
        const rect = triggerRef.current.getBoundingClientRect();
        const menuHeight = 250; // Altura aproximada del menú
        const spaceBelow = window.innerHeight - rect.bottom;
        const openUpward = spaceBelow < menuHeight && rect.top > menuHeight;
        
        setPosition({
          top: openUpward ? rect.top - menuHeight : rect.bottom + 4,
          left: rect.right - 160, // 160px es el ancho mínimo del menú
          openUpward,
        });
      }
    } else {
      setPosition(null);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleItemClick = (item: ActionMenuItem) => {
    item.onClick();
    setIsOpen(false);
  };

  const dropdownContent = isOpen && position && (
    <div 
      ref={menuRef}
      className={`action-menu-dropdown ${position.openUpward ? 'open-upward' : ''}`}
      style={{
        position: 'fixed',
        top: `${position.top}px`,
        left: `${position.left}px`,
      }}
    >
      {items.map((item, index) => (
        <button
          key={index}
          className={`action-menu-item action-menu-item-${item.variant || 'default'}`}
          onClick={(e) => {
            e.stopPropagation();
            handleItemClick(item);
          }}
        >
          {item.icon && <span className="action-menu-icon">{item.icon}</span>}
          <span>{item.label}</span>
        </button>
      ))}
    </div>
  );

  return (
    <>
      <div className="action-menu">
        <button
          ref={triggerRef}
          className="action-menu-trigger"
          onClick={(e) => {
            e.stopPropagation();
            setIsOpen(!isOpen);
          }}
          aria-label="Más acciones"
        >
          <span className="action-menu-dots">⋮</span>
        </button>
      </div>
      {dropdownContent && createPortal(dropdownContent, document.body)}
    </>
  );
};

export default ActionMenu;
