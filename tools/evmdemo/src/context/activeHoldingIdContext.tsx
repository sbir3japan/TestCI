import { type } from "os";
import React from "react";

type ActiveHoldingIdContextType = {
  activeHoldingId: string;
  setActiveHoldingId: React.Dispatch<React.SetStateAction<string>>;
};

export const ActiveHoldingIdContext =
  React.createContext<ActiveHoldingIdContextType>({
    activeHoldingId: "C04E17844E72",
    setActiveHoldingId: () => {},
  });
