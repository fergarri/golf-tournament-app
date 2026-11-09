import { FormEvent, useEffect, useState } from 'react';
import { authService } from '../services/authService';
import { useAuth } from '../hooks/useAuth';
import { UserProfile } from '../types';
import Modal from '../components/Modal';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrador',
  ADMIN_CLUB: 'Administrador de club',
  USER: 'Usuario',
};

const clubLabel = (profile: UserProfile) => {
  if (profile.courseName) return profile.courseName;
  if (profile.role === 'ADMIN') return 'Todos los clubes';
  return '—';
};

const ProfilePage = () => {
  const { updateCurrentUser } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [successOpen, setSuccessOpen] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const data = await authService.getProfile();
        setProfile(data);
        setEmail(data.email);
        setError('');
      } catch (err: any) {
        setError(err.response?.data?.message || 'Error cargando el perfil');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      setSaving(true);
      const updated = await authService.updateProfile({ email: email.trim() });
      setProfile(updated);
      setEmail(updated.email);
      updateCurrentUser({ email: updated.email }, updated.token);
      setSuccessOpen(true);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error actualizando el perfil');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="loading">Cargando perfil...</div>;

  return (
    <div>
      <div className="page-header">
        <h1>Perfil</h1>
      </div>

      <Card className="max-w-lg">
        <CardHeader>
          <CardTitle className="text-xl">Datos del usuario</CardTitle>
          <CardDescription>Podés actualizar tu email. La matrícula no se puede modificar.</CardDescription>
        </CardHeader>
        <CardContent>
          {profile ? (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="profile-email">Email</Label>
                <Input
                  id="profile-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="profile-matricula">Matrícula</Label>
                <Input
                  id="profile-matricula"
                  value={profile.matricula || '—'}
                  disabled
                />
                <p className="text-xs text-slate-500">La matrícula no se puede modificar.</p>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="profile-role">Rol</Label>
                <Input
                  id="profile-role"
                  value={ROLE_LABELS[profile.role] || profile.role}
                  disabled
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="profile-club">Club</Label>
                <Input
                  id="profile-club"
                  value={clubLabel(profile)}
                  disabled
                />
              </div>

              {error && (
                <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-md px-3 py-2">
                  {error}
                </div>
              )}

              <Button type="submit" disabled={saving}>
                {saving ? 'Guardando...' : 'Guardar'}
              </Button>
            </form>
          ) : (
            error && (
              <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-md px-3 py-2">
                {error}
              </div>
            )
          )}
        </CardContent>
      </Card>

      <Modal
        isOpen={successOpen}
        onClose={() => setSuccessOpen(false)}
        title="Perfil actualizado"
        message="Tus datos se guardaron correctamente."
        type="success"
      />
    </div>
  );
};

export default ProfilePage;
