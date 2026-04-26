import { useEffect, ReactNode } from 'react';
import { X, CheckCircle2, XCircle, AlertTriangle, Info, HelpCircle } from 'lucide-react';
import { Button } from './ui/button';
import { cn } from '@/lib/utils';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm?: () => void;
  title: string;
  message?: string;
  type?: 'info' | 'success' | 'warning' | 'error' | 'confirm';
  confirmText?: string;
  cancelText?: string;
  size?: 'small' | 'medium' | 'large';
  children?: ReactNode;
  footer?: ReactNode;
}

const sizeClasses = {
  small: 'max-w-sm',
  medium: 'max-w-lg',
  large: 'max-w-2xl',
};

const typeConfig = {
  success: { icon: CheckCircle2, iconClass: 'text-emerald-500', bgClass: 'bg-emerald-50' },
  error: { icon: XCircle, iconClass: 'text-red-500', bgClass: 'bg-red-50' },
  warning: { icon: AlertTriangle, iconClass: 'text-amber-500', bgClass: 'bg-amber-50' },
  confirm: { icon: HelpCircle, iconClass: 'text-blue-500', bgClass: 'bg-blue-50' },
  info: { icon: Info, iconClass: 'text-sky-500', bgClass: 'bg-sky-50' },
};

const Modal = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  type = 'info',
  confirmText = 'OK',
  cancelText = 'Cancelar',
  size = 'small',
  children,
  footer,
}: ModalProps) => {
  useEffect(() => {
    document.body.style.overflow = isOpen ? 'hidden' : 'unset';
    return () => { document.body.style.overflow = 'unset'; };
  }, [isOpen]);

  if (!isOpen) return null;

  const handleConfirm = () => {
    onConfirm?.();
    onClose();
  };

  const config = typeConfig[type];
  const Icon = config.icon;

  return (
    /* El overlay hace scroll cuando el contenido es más alto que la pantalla */
    <div
      className="fixed inset-0 z-50 overflow-y-auto bg-black/50 backdrop-blur-sm"
      onClick={onClose}
    >
      <div className="flex min-h-full items-center justify-center p-4">
        <div
          className={cn(
            'relative w-full bg-white rounded-xl shadow-xl flex flex-col',
            sizeClasses[size],
            /* Máximo alto = viewport menos padding, el body hace scroll internamente */
            'max-h-[calc(100vh-2rem)]'
          )}
          onClick={(e) => e.stopPropagation()}
        >
          {children ? (
            /* Custom content mode — header y footer fijos, body scrollable */
            <>
              <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 flex-shrink-0">
                <h2 className="text-lg font-semibold text-slate-800">{title}</h2>
                <button
                  onClick={onClose}
                  className="p-1.5 rounded-md text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="p-6 overflow-y-auto flex-1">{children}</div>
              {footer && (
                <div className="px-6 py-4 border-t border-slate-200 flex-shrink-0">
                  {footer}
                </div>
              )}
            </>
          ) : (
            /* Alert / Confirm mode */
            <div className="p-6 text-center overflow-y-auto">
              <div className={cn('mx-auto w-14 h-14 rounded-full flex items-center justify-center mb-4', config.bgClass)}>
                <Icon className={cn('h-7 w-7', config.iconClass)} />
              </div>
              <h2 className="text-lg font-semibold text-slate-800 mb-2">{title}</h2>
              {message && <p className="text-slate-500 text-sm mb-6">{message}</p>}
              <div className="flex gap-3 justify-center">
                {type === 'confirm' && (
                  <Button variant="outline" onClick={onClose}>
                    {cancelText}
                  </Button>
                )}
                <Button
                  variant={type === 'error' || type === 'confirm' ? (type === 'error' ? 'destructive' : 'default') : 'default'}
                  onClick={handleConfirm}
                >
                  {confirmText}
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Modal;
