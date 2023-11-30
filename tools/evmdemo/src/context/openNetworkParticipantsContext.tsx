import React from "react";

type OpenNetworkParticipantsContextType = {
  openNetworkParticipants: boolean;
  setOpenNetworkParticipants: React.Dispatch<React.SetStateAction<boolean>>;
};

export const OpenNetworkParticipantsContext =
  React.createContext<OpenNetworkParticipantsContextType>({
    openNetworkParticipants: false,
    setOpenNetworkParticipants: () => {},
  });
