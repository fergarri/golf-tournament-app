import { useState, FormEvent } from 'react';
import { useAuth } from '../hooks/useAuth';
import './LoginPage.css';

const LoginPage = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();

  const handleSubmit = async (e?: FormEvent | React.MouseEvent) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    
    console.log('Login attempt started');
    setError('');
    setIsLoading(true);

    try {
      console.log('Calling login service...');
      await login(username, password);
      console.log('Login successful');
    } catch (err: any) {
      console.error('Login error:', err);
      console.error('Error response:', err.response);
      const errorMessage = err.response?.data?.message || err.message || 'Invalid email or password';
      console.log('Setting error message:', errorMessage);
      setError(errorMessage);
      setIsLoading(false);
      console.log('Error state set, isLoading:', false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <h1>Golf Tournament</h1>
        <h2>Admin Login</h2>
        <form onSubmit={(e) => { e.preventDefault(); handleSubmit(e); }}>
          <div className="form-group">
            <label htmlFor="username">Email or Registration Number</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter your email or registration number"
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
            />
          </div>
          {error && <div className="error-message">{error}</div>}
          <button type="submit" className="btn-primary" disabled={isLoading}>
            {isLoading ? 'Logging in...' : 'Login'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default LoginPage;
