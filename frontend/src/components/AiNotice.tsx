import { Link } from 'react-router-dom';
import { PlugZap } from 'lucide-react';
import { useStatus } from '../context';

/** Shown wherever AI features are offered but no model is connected. */
export default function AiNotice() {
  const { status, error } = useStatus();
  if (error) {
    return (
      <div className="notice notice-error">
        <PlugZap size={18} />
        <div><strong>The backend is not reachable.</strong> {error}</div>
      </div>
    );
  }
  if (!status || status.ai.configured) return null;
  return (
    <div className="notice">
      <PlugZap size={18} />
      <div>
        <strong>No AI model is connected, so code generation is turned off.</strong> You can still create, edit, preview,
        check and download projects by hand. <Link to="/settings">See how to connect a model</Link>.
      </div>
    </div>
  );
}
