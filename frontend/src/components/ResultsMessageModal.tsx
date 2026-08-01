import Modal from '../components/Modal';
import '../components/Form.css';

interface ResultsMessageModalProps {
  message: string | null;
  onClose: () => void;
}

const ResultsMessageModal = ({ message, onClose }: ResultsMessageModalProps) => {
  return (
    <Modal
      isOpen={message != null}
      onClose={onClose}
      title="Mensaje Copiado"
      size="medium"
    >
      <div>
        <pre
          style={{
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            textAlign: 'left',
            background: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '1rem',
            marginBottom: '1.25rem',
            fontFamily: 'inherit',
            fontSize: '0.9rem',
            lineHeight: 1.5,
            color: '#334155',
          }}
        >
          {message}
        </pre>
        <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem' }}>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              if (message) {
                navigator.clipboard.writeText(message);
              }
            }}
          >
            Copiar
          </button>
          <button type="button" className="btn btn-danger" onClick={onClose}>
            Cerrar
          </button>
        </div>
      </div>
    </Modal>
  );
};

export default ResultsMessageModal;
