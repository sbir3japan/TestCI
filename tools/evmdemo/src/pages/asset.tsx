import React from "react";
import Asset from "@/views/asset";
import { useRouter } from "next/router";
import Layout from "../components/Layout";
export default function AssetPage() {
  const router = useRouter();
  const queryParams = router.query;
  return (
    <Layout>
      <Asset
        queryParams={queryParams}
        router={router}
        setOpenNetworkParticipants={() => {}}
      />
    </Layout>
  );
}
