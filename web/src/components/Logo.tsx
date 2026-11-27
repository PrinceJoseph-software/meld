import { navigate } from '../router';
import { Mark } from './Mark';

export function TopBar({ right }: { right?: React.ReactNode }) {
  return (
    <header className="topbar">
      <a
        className="brand"
        href="/app"
        onClick={(e) => {
          e.preventDefault();
          navigate('/app');
        }}
      >
        <Mark size={34} />
        Meld
      </a>
      {right}
    </header>
  );
}
