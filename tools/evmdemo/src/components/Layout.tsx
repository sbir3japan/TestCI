"use client";
import React from "react";
import "./../styles/global.css";
import Navbar from "@/components/Navbar";
import { OpenNetworkParticipantsContext } from "@/context/openNetworkParticipantsContext";
import ParticipantsPopUp from "@/components/ParticipantsPopUp";
import { ActiveHoldingIdContext } from "@/context/activeHoldingIdContext";

type LayoutProps = {
  children: React.ReactNode;
};

function Layout({ children }: LayoutProps) {
  const [openNetworkParticipants, setOpenNetworkParticipants] =
    React.useState(false);
  const [activeHoldingId, setActiveHoldingId] = React.useState("C04E17844E72");
  const modifiedChildElement = React.cloneElement(children, {
    setOpenNetworkParticipants,
  });

  return (
    <>
      <OpenNetworkParticipantsContext.Provider
        value={{ openNetworkParticipants, setOpenNetworkParticipants }}
      >
        <ActiveHoldingIdContext.Provider
          value={{ activeHoldingId, setActiveHoldingId }}
        >
          <Navbar activeHoldingId={activeHoldingId} />
          <ParticipantsPopUp
            activeHoldingId={activeHoldingId}
            setActiveHoldingId={setActiveHoldingId}
            openNetworkParticipants={openNetworkParticipants}
            setOpenNetworkParticipants={setOpenNetworkParticipants}
          />

          {modifiedChildElement}
        </ActiveHoldingIdContext.Provider>
      </OpenNetworkParticipantsContext.Provider>
    </>
  );
}

export default Layout;
