export type AuthUrlResponse = {
    endpointUrl: string
    request: string
}

export type ApiErrorBody = {
    status: string
    message: string
}

export type UserData = {
    name: string
    idToken: string
    exp: number
    identityRawData: string
}

export type UserDataResponse = Required<UserData>

export type InfoMessageResponse = {
    message: string
}

export type ResourceMessageResponse = InfoMessageResponse
