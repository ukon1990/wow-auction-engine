export const supplementalTestFixtures = [
    {
        endpointPath: "auction/malformed-auction-data",
        payload: '{"auctions":[{"id":1',
        raw: true,
    },
    {
        endpointPath: "auction/upstream-error",
        payload: {
            error: "upstream",
        },
    },
];
