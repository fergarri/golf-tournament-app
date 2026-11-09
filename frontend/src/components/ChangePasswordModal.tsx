import { FormEvent, useEffect, useState } from 'react';
import Modal from './Modal';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { PASSWORD_HINT, validateNewPassword } from '../utils/passwordPolicy';

interface ChangePasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: { currentPassword?: string; newPassword: string }) => Promise<void>;
  onSuccess?: () => void;
  requireCurrentPassword: boolean;
  description?: string;
  formId?: string;
}

const ChangePasswordModal = ({
  isOpen,
  onClose,
  onSubmit,
  onSuccess,
  requireCurrentPassword,
  description,
  formId = 'change-password-form',
}: ChangePasswordModalProps) => {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setError('');
    setSaving(false);
  }, [isOpen]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');

    if (requireCurrentPassword && !currentPassword) {
      setError('La contraseña actual es obligatoria');
      return;
    }

    const formatError = validateNewPassword(newPassword);
    if (formatError) {
      setError(formatError);
      return;
    }

    if (requireCurrentPassword && newPassword === currentPassword) {
      setError('La nueva contraseña no puede ser igual a la actual');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('La confirmación no coincide con la nueva contraseña');
      return;
    }

    try {
      setSaving(true);
      await onSubmit({
        currentPassword: requireCurrentPassword ? currentPassword : undefined,
        newPassword,
      });
      onSuccess?.();
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error cambiando la contraseña');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Cambiar contraseña"
      size="medium"
      footer={
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={saving}>
            Cancelar
          </Button>
          <Button type="submit" form={formId} disabled={saving}>
            {saving ? 'Guardando...' : 'Cambiar contraseña'}
          </Button>
        </div>
      }
    >
      {description && <p className="text-sm text-slate-500 mb-4">{description}</p>}
      <form id={formId} onSubmit={handleSubmit} className="space-y-4">
        {requireCurrentPassword && (
          <div className="space-y-1.5">
            <Label htmlFor={`${formId}-current`}>Contraseña actual</Label>
            <Input
              id={`${formId}-current`}
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
        )}
        <div className="space-y-1.5">
          <Label htmlFor={`${formId}-new`}>Nueva contraseña</Label>
          <Input
            id={`${formId}-new`}
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor={`${formId}-confirm`}>Confirmar contraseña</Label>
          <Input
            id={`${formId}-confirm`}
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
        </div>
        <p className="text-xs text-slate-500 leading-relaxed">{PASSWORD_HINT}</p>
        {error && (
          <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-md px-3 py-2">
            {error}
          </div>
        )}
      </form>
    </Modal>
  );
};

export default ChangePasswordModal;
