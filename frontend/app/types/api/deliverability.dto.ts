export interface DeliverabilityCheckApiDto {
  id: string
  domain: string
  spfStatus: string | null
  spfRecord: string | null
  dkimStatus: string | null
  dkimSelector: string | null
  dkimRecord: string | null
  dmarcStatus: string | null
  dmarcRecord: string | null
  checkedAt: string
}
