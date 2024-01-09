import React from "react";
import { useRouter } from "next/router";

const IndexPage: React.FC = () => {
  const history = useRouter();

  const navigateToAssetsPage = () => {
    history.push("/assets");
  };

  const evmSwapsDemoPage = () => {
    history.push("/harmonia");
  };

  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-evenly",
        alignItems: "center",
        height: "100vh",
      }}
    >
      <button
        style={{
          backgroundColor: "blue",
          color: "white",
          fontFamily: "Roboto",
          padding: "10px 20px",
          borderRadius: "5px",
          cursor: "pointer",
        }}
        onClick={evmSwapsDemoPage}
      >
        Harmonia
      </button>
      <button
        style={{
          backgroundColor: "red",
          color: "white",
          fontFamily: "Roboto",
          padding: "10px 20px",
          borderRadius: "5px",
          cursor: "pointer",
        }}
        onClick={navigateToAssetsPage}
      >
        Fractional Asset Demo
      </button>
    </div>
  );
};

export default IndexPage;
