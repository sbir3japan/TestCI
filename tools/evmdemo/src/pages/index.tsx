import React from 'react';
import { useRouter } from 'next/router';

const IndexPage: React.FC = () => {
  const history = useRouter();

  const navigateToAssetsPage = () => {
    history.push('/assets');
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
      <button
        style={{
          backgroundColor: 'red',
          color: 'white',
          fontFamily: 'Roboto',
          padding: '10px 20px',
          borderRadius: '5px',
          cursor: 'pointer',
        }}
        onClick={navigateToAssetsPage}
      >
        Go to Demo
      </button>
    </div>
  );
};

export default IndexPage;
